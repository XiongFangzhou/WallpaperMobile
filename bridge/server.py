"""Authenticated LAN bridge; Python standard library only."""
import argparse
import datetime as dt
import hmac
import ipaddress
import json
import mimetypes
import re
import secrets
import socket
import threading
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from .jobs import Jobs, valid_item_id
from .steam import Steam, SteamError

ROOT = Path(__file__).resolve().parents[1]


def mime(name):
    return mimetypes.guess_type(name)[0] or 'application/octet-stream'


class BridgeServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, steam, jobs, token):
        self.steam, self.jobs, self.token = steam, jobs, token
        self.previews = {}
        self.preview_lock = threading.Lock()
        self.preview_slots = threading.BoundedSemaphore(4)
        self.acknowledged = {}  # In-memory only: ACK retry after a lost response.
        super().__init__(address, Handler)


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    server_version = 'VideoBridge/0.1'

    def setup(self):
        super().setup()
        self.connection.settimeout(60)

    def log_message(self, *_args):
        pass  # Avoid retaining workshop history, pairing tokens, or phone requests.

    def send_bytes(self, data, content_type='application/json; charset=utf-8', status=200, extra=None):
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'no-referrer')
        for k, v in (extra or {}).items():
            self.send_header(k, str(v))
        self.end_headers()
        self.wfile.write(data)

    def json(self, value, status=200):
        self.send_bytes(json.dumps(value, ensure_ascii=False).encode('utf-8'), status=status)

    def authorized(self):
        supplied = self.headers.get('Authorization', '')
        return hmac.compare_digest(supplied, 'Bearer ' + self.server.token)

    def route(self):
        p = urllib.parse.urlsplit(self.path)
        return p.path, urllib.parse.parse_qs(p.query, keep_blank_values=True)

    def do_GET(self):
        try:
            path, query = self.route()
            if path == '/api/pairing':
                origin = self.headers.get('Origin')
                host = urllib.parse.urlsplit('http://' + self.headers.get('Host', '')).hostname
                if host not in ('localhost', '127.0.0.1', '::1') or not ipaddress.ip_address(self.client_address[0]).is_loopback or (origin and urllib.parse.urlsplit(origin).netloc != self.headers.get('Host')):
                    return self.json({'error': '配对信息仅能在电脑本机查看。'}, 403)
                return self.json({'token': self.server.token, 'port': self.server.server_port, 'addresses': local_addresses()})
            if path in ('/', '/app.js', '/style.css'):
                name = 'index.html' if path == '/' else path[1:]
                return self.send_bytes((ROOT / 'web' / name).read_bytes(), mime(name), extra={
                    'Content-Security-Policy': "default-src 'self'; img-src 'self' blob:; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'"})
            if path == '/download/app.apk':
                apk = ROOT / 'dist' / 'WallpaperVideoBridge.apk'
                if not apk.is_file():
                    return self.json({'error': 'APK 尚未构建。'}, 404)
                return self.send_bytes(apk.read_bytes(), 'application/vnd.android.package-archive')
            if not self.authorized():
                return self.json({'error': '配对码无效，请重新连接电脑。'}, 401)
            if path == '/api/status':
                return self.json({'connected': True, 'app': 'Wallpaper Video Bridge', 'version': '0.1.0', 'jobs': self.server.jobs.list()})
            if path == '/api/catalog':
                one = lambda k, default='': query.get(k, [default])[0]
                tags = query.get('tag', [])
                if len(tags) > 10 or any(len(t) > 80 for t in tags) or len(one('q')) > 200:
                    raise ValueError('搜索参数过长。')
                def stamp(key):
                    value = one(key)
                    return int(dt.datetime.strptime(value, '%Y-%m-%d').replace(tzinfo=dt.timezone(dt.timedelta(hours=8))).timestamp()) if value else 0
                start, end = stamp('from'), stamp('to')
                if end:
                    end += 86399
                if start and end and start > end:
                    raise ValueError('开始日期不能晚于结束日期。')
                result = self.server.steam.query(text=one('q'), sort=one('sort', 'trending'),
                    page=int(one('page', '1')), tags=tags, days=max(1, min(365, int(one('days', '7')))),
                    created_after=start, created_before=end)
                with self.server.preview_lock:
                    if len(self.server.previews) > 2000:
                        self.server.previews.clear()
                    self.server.previews.update({i['id']: i['preview'] for i in result['items']})
                return self.json(result)
            if path == '/api/jobs':
                return self.json({'jobs': self.server.jobs.list()})
            match = re.fullmatch(r'/api/jobs/([0-9a-f]{32})(/file)?', path)
            if match:
                if match[2]:
                    return self.transfer(match[1])
                return self.json(self.server.jobs.public(self.server.jobs.get(match[1])))
            match = re.fullmatch(r'/api/preview/([1-9][0-9]{0,19})', path)
            if match:
                return self.preview(match[1])
            return self.json({'error': '地址不存在。'}, 404)
        except (ValueError, KeyError) as e:
            self.json({'error': str(e).strip("'")}, 400)
        except SteamError as e:
            self.json({'error': str(e)}, 502)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            self.close_connection = True
        except Exception:
            self.json({'error': '电脑端处理失败，请检查磁盘空间和 Steam 状态。'}, 500)

    def do_POST(self):
        if not self.authorized():
            return self.json({'error': '配对码无效。'}, 401)
        origin = self.headers.get('Origin')
        if origin and urllib.parse.urlsplit(origin).netloc != self.headers.get('Host'):
            return self.json({'error': '请求来源无效。'}, 403)
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length < 8192:
                raise ValueError('请求内容大小无效。')
            payload = json.loads(self.rfile.read(length))
            if not isinstance(payload, dict):
                raise ValueError('请求格式无效。')
            path, _ = self.route()
            if path == '/api/jobs':
                return self.json(self.server.jobs.create(valid_item_id(payload.get('item_id', ''))), 201)
            match = re.fullmatch(r'/api/jobs/([0-9a-f]{32})/(ack|cancel)', path)
            if match:
                job_id, operation = match.groups()
                if operation == 'cancel':
                    return self.json(self.server.jobs.cancel(job_id))
                with self.server.jobs.lock:
                    if job_id in self.server.acknowledged:
                        old = self.server.acknowledged[job_id]
                        if payload.get('saved') is True and payload.get('sha256') == old['sha256']:
                            return self.json(old['result'])
                        raise ValueError('确认信息无效。')
                    result = self.server.jobs.acknowledge(job_id, payload.get('sha256'), payload.get('saved'))
                    if result['cleaned']:
                        if len(self.server.acknowledged) >= 100:
                            self.server.acknowledged.pop(next(iter(self.server.acknowledged)))
                        self.server.acknowledged[job_id] = {'sha256': payload['sha256'], 'result': result}
                return self.json(result)
            return self.json({'error': '地址不存在。'}, 404)
        except (ValueError, KeyError) as e:
            self.json({'error': str(e).strip("'")}, 400)
        except SteamError as e:
            self.json({'error': str(e)}, 502)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            self.close_connection = True
        except Exception:
            self.json({'error': '电脑端处理失败，文件已保留。'}, 500)

    def preview(self, item_id):
        with self.server.preview_lock:
            url = self.server.previews.get(item_id)
        if not url:
            return self.send_bytes(b'', 'image/jpeg', 404)
        parsed = urllib.parse.urlsplit(url)
        host = parsed.hostname or ''
        if parsed.scheme != 'https' or not any(host.endswith('.' + domain) or host == domain
                for domain in ('steamusercontent.com', 'steamstatic.com', 'akamaihd.net')):
            return self.send_bytes(b'', 'image/jpeg', 400)
        try:
            with self.server.preview_slots:
                with urllib.request.urlopen(url, timeout=18) as r:
                    data = r.read(8 * 1024 ** 2 + 1)
                    content_type = r.headers.get_content_type()
            if len(data) > 8 * 1024 ** 2 or not content_type.startswith('image/'):
                raise ValueError('Invalid preview')
            return self.send_bytes(data, content_type)
        except (OSError, ValueError):
            return self.send_bytes(b'', 'image/jpeg', 502)

    def transfer(self, job_id):
        path, job = self.server.jobs.begin_transfer(job_id)
        try:
            total = path.stat().st_size
            start, end, status = 0, total - 1, 200
            requested = self.headers.get('Range')
            if requested:
                m = re.fullmatch(r'bytes=(\d+)-(\d*)', requested)
                if not m:
                    return self.send_bytes(b'', status=416, extra={'Content-Range': f'bytes */{total}'})
                start, end = int(m[1]), min(int(m[2]) if m[2] else total - 1, total - 1)
                if start > end or start >= total:
                    return self.send_bytes(b'', status=416, extra={'Content-Range': f'bytes */{total}'})
                status = 206
            self.send_response(status)
            self.send_header('Content-Type', mime(job['name']))
            self.send_header('Content-Length', str(end - start + 1))
            self.send_header('Accept-Ranges', 'bytes')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-SHA256', job['sha256'])
            self.send_header('Content-Disposition', "attachment; filename*=UTF-8''" + urllib.parse.quote(job['name']))
            if status == 206:
                self.send_header('Content-Range', f'bytes {start}-{end}/{total}')
            self.end_headers()
            with path.open('rb') as f:
                f.seek(start)
                remaining = end - start + 1
                while remaining:
                    chunk = f.read(min(1024 * 1024, remaining))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    remaining -= len(chunk)
        finally:
            self.server.jobs.end_transfer(job_id)


def local_addresses():
    addresses = set()
    try:
        addresses.update(socket.gethostbyname_ex(socket.gethostname())[2])
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(('192.0.2.1', 80))
            addresses.add(s.getsockname()[0])
    except OSError:
        pass
    return sorted(x for x in addresses if not x.startswith(('127.', '169.254.')))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', default='0.0.0.0')
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--dll', required=True, help='Path to the installed Wallpaper Engine steam_api64.dll')
    args = parser.parse_args()
    runtime = ROOT / 'runtime'
    runtime.mkdir(exist_ok=True)
    token_path = runtime / 'pairing.key'
    if not token_path.exists():
        token_path.write_text(secrets.token_hex(24), encoding='ascii')
    token = token_path.read_text(encoding='ascii').strip()
    steam = Steam(args.dll)
    content = Path(args.dll).resolve().parents[3] / 'workshop' / 'content' / '431960'
    jobs = Jobs(steam, runtime, [content])
    server = BridgeServer((args.host, args.port), steam, jobs, token)
    print(f'电脑管理页面: http://127.0.0.1:{args.port}/', flush=True)
    for address in local_addresses():
        print(f'手机连接地址: http://{address}:{args.port}/#token={token}', flush=True)
    print('保持 Steam 登录。Ctrl+C 退出。', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        # Pending Steam callbacks may still be needed by the preparation worker.
        jobs.stopping.set()
        jobs.pool.shutdown(wait=True, cancel_futures=True)
        steam.close()


if __name__ == '__main__':
    main()
