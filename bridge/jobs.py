"""Durable transfer jobs; delete only owned content after a verified phone ACK."""
import hashlib
import json
import os
import re
import shutil
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

VIDEO_EXTENSIONS = {'.mp4', '.webm', '.mkv', '.mov', '.m4v', '.avi'}


def valid_item_id(value):
    value = str(value)
    if not re.fullmatch(r'[1-9][0-9]{0,19}', value) or int(value) >= 2 ** 64:
        raise ValueError('作品 ID 无效。')
    return value


def snapshot(directory):
    result = {}
    if directory.is_symlink() or directory.is_junction():
        raise ValueError('不处理链接目录。')
    for p in directory.rglob('*'):
        if p.is_symlink() or p.is_junction():
            raise ValueError('资源包含链接，不能自动清理。')
        if p.is_file():
            s = p.stat()
            result[str(p.relative_to(directory))] = [s.st_size, s.st_mtime_ns]
    return result


def video_file(directory):
    directory = directory.resolve()
    project = directory / 'project.json'
    if not project.is_file() or project.is_symlink():
        raise ValueError('资源缺少 project.json，无法确认原始视频。')
    info = json.loads(project.read_text(encoding='utf-8-sig'))
    if str(info.get('type', '')).lower() != 'video':
        raise ValueError('这个作品不是原始视频类型，不能直接保存为视频。')
    relative = str(info.get('file', ''))
    if not relative or Path(relative).is_absolute():
        raise ValueError('资源视频路径无效。')
    path = (directory / relative).resolve()
    if not path.is_relative_to(directory) or path.suffix.lower() not in VIDEO_EXTENSIONS or not path.is_file():
        raise ValueError('资源中未找到可直接传输的视频。')
    return path, info


class Jobs:
    PUBLIC = ('id', 'item_id', 'title', 'state', 'received', 'total', 'size',
              'name', 'sha256', 'error', 'note', 'created', 'cancel_requested')

    def __init__(self, steam, runtime, content_roots):
        self.steam = steam
        self.root = Path(runtime).resolve() / 'jobs'
        self.root.mkdir(parents=True, exist_ok=True)
        self.content_roots = [Path(p).resolve() for p in content_roots]
        self.lock = threading.RLock()
        self.pool = ThreadPoolExecutor(max_workers=1, thread_name_prefix='video-transfer')
        self.jobs = {}
        self.active = {}
        self.stopping = threading.Event()
        for d in self.root.iterdir():
            if not d.is_dir() or not re.fullmatch('[0-9a-f]{32}', d.name):
                continue
            try:
                job = json.loads((d / 'job.json').read_text(encoding='utf-8'))
                if job['id'] != d.name:
                    continue
                job['state'] = 'ready' if job.get('sha256') and (d / 'video').is_file() else 'error'
                job['error'] = '' if job['state'] == 'ready' else '上次运行中断，可删除此任务后重试。'
                self.jobs[job['id']] = job
                self._save(job)
            except (OSError, ValueError, KeyError):
                continue

    def _save(self, job):
        directory = self.root / job['id']
        directory.mkdir(exist_ok=True)
        temporary = directory / 'job.next'
        temporary.write_text(json.dumps(job, ensure_ascii=False), encoding='utf-8')
        os.replace(temporary, directory / 'job.json')

    def _update(self, job, **fields):
        with self.lock:
            job.update(fields)
            self._save(job)

    def public(self, job):
        return {k: job[k] for k in self.PUBLIC if k in job}

    def list(self):
        with self.lock:
            return [self.public(x) for x in self.jobs.values()]

    def get(self, job_id):
        if not re.fullmatch('[0-9a-f]{32}', job_id):
            raise ValueError('任务 ID 无效。')
        with self.lock:
            if job_id not in self.jobs:
                raise KeyError('任务已完成清理或不存在。')
            return self.jobs[job_id]

    def create(self, item_id):
        item_id = valid_item_id(item_id)
        with self.lock:
            for j in self.jobs.values():
                if j['item_id'] == item_id:
                    return self.public(j)
            if len(self.jobs) >= 20:
                raise ValueError('待处理任务过多，请先保存或清理已有任务。')
            existing = [str(p / item_id) for p in self.content_roots if (p / item_id).exists()]
            state = self.steam.state(item_id)
            job = {'id': uuid.uuid4().hex, 'item_id': item_id, 'title': item_id,
                   'state': 'queued', 'created': time.time(), 'received': 0, 'total': 0,
                   'original_state': state, 'preexisting': bool(existing) or bool(state & 7),
                   'cancel_requested': False, 'error': '', 'note': ''}
            self.jobs[job['id']] = job
            self._save(job)
            self.pool.submit(self._prepare, job)
            return self.public(job)

    def _prepare(self, job):
        try:
            if job['cancel_requested']:
                self._update(job, state='cancelled')
                self.cancel(job['id'])
                return
            item_id = job['item_id']
            info = self.steam.details(item_id)
            if not any(t.lower() == 'video' for t in info['tags']):
                raise ValueError('这个作品不是 Video 视频类型，请选择视频作品。')
            self._update(job, title=info['title'])
            state = self.steam.state(item_id)
            # Preserve existing installed files. Never update them just for a transfer.
            directory = self.steam.install_info(item_id) if state & 4 and not state & 48 else None
            if not directory:
                self._update(job, state='downloading')
                self.steam.start_download(item_id)
                deadline = time.monotonic() + 3600
                while True:
                    if self.stopping.is_set():
                        raise ValueError('电脑服务已退出；Steam 可能继续下载，重新启动后检查任务。')
                    done, total, result = self.steam.progress(item_id)
                    self._update(job, received=done, total=total)
                    if result is not None:
                        if result != 1:
                            raise ValueError(f'Steam 下载失败，错误码 {result}。')
                        break
                    if time.monotonic() > deadline:
                        raise ValueError('Steam 下载超时；资源仍可能在下载，请稍后检查任务。')
                    time.sleep(0.5)
                directory = self.steam.install_info(item_id)
            if not directory:
                raise ValueError('Steam 未返回下载目录。')
            directory = Path(directory).resolve()
            self._update(job, source_dir=str(directory))
            if not job['preexisting']:
                self._update(job, source_snapshot=snapshot(directory))
            if job['cancel_requested']:
                self._update(job, state='cancelled')
                self.cancel(job['id'])
                return
            self._update(job, state='preparing')
            source, project = video_file(directory)
            if shutil.disk_usage(self.root).free < source.stat().st_size + 128 * 1024 ** 2:
                raise ValueError('E 盘剩余空间不足，无法准备视频。')
            output = self.root / job['id'] / 'video'
            before = source.stat()
            digest = hashlib.sha256()
            with source.open('rb') as src, output.open('wb') as dst:
                while chunk := src.read(1024 * 1024):
                    if job['cancel_requested']:
                        raise ValueError('任务已取消，可清理任务。')
                    dst.write(chunk)
                    digest.update(chunk)
            after = source.stat()
            if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                raise ValueError('原始视频在准备期间发生变化，请重试。')
            title = re.sub(r'[<>:"/\\|?*\x00-\x1f]', '_', info['title']).strip(' .')[:90] or item_id
            self._update(job, state='ready', name=f'{title}_{item_id}{source.suffix.lower()}',
                         size=output.stat().st_size, sha256=digest.hexdigest(),
                         note='原有 Steam 资源会保留。' if job['preexisting'] else '手机校验保存后清理本次新增资源。')
            if job['cancel_requested']:
                self.cancel(job['id'])
        except Exception as exc:
            self._update(job, state='error', error=str(exc))

    def _cleanup_source(self, job):
        if job['preexisting']:
            return '已保留原有 Steam 资源。'
        directory = job.get('source_dir')
        if not directory:
            # A failed Steam download can still be active. Do not claim it was removed.
            if self.steam.state(job['item_id']) & 48:
                raise ValueError('Steam 仍在下载，暂不能清理。请下载结束后重试。')
            directory = self.steam.install_info(job['item_id'])
            if directory:
                raise ValueError('任务未完成资源归属核验，已保留 Steam 缓存，请人工检查。')
            return '没有已完成的新增资源。Steam 自身缓存可能仍存在。'
        raw_directory = Path(directory)
        directory = raw_directory.resolve()
        expected = [p / job['item_id'] for p in self.content_roots]
        if directory not in expected or raw_directory.is_symlink() or raw_directory.is_junction():
            raise ValueError('Steam 返回的目录不在允许清理的范围，已保留。')
        if self.steam.state(job['item_id']) & (1 | 16 | 32):
            raise ValueError('资源已被订阅或仍在下载，暂不清理 Steam 文件。')
        if not directory.exists():
            return '新增 Steam 资源已不存在。'
        recorded = job.get('source_snapshot')
        if recorded is None or snapshot(directory) != recorded:
            raise ValueError('Steam 资源内容已变化，为保护文件没有清理。')
        # Resolve again immediately before mutation; never follow a replaced junction.
        if directory.resolve() not in expected or directory.is_junction():
            raise ValueError('资源目录位置已改变，已保留。')
        self.steam.mark_unused(job['item_id'])
        if directory.exists():
            shutil.rmtree(directory)
        return '已清理本次新增 Steam 资源；Steam 自身日志不在清理范围。'

    def acknowledge(self, job_id, sha256, saved):
        with self.lock:
            job = self.get(job_id)
            if saved is not True or job['state'] != 'ready' or sha256 != job.get('sha256'):
                raise ValueError('接收校验未通过，电脑副本已保留。')
            if self.active.get(job_id, 0):
                raise ValueError('传输连接尚未关闭，请稍后重试清理。')
            try:
                note = self._cleanup_source(job)
            except (OSError, ValueError) as exc:
                job['note'] = str(exc)
                self._save(job)
                return {'cleaned': False, 'note': str(exc)}
            self._remove(job)
            return {'cleaned': True, 'note': note}

    def cancel(self, job_id):
        with self.lock:
            job = self.get(job_id)
            job['cancel_requested'] = True
            self._save(job)
            if job['state'] in ('downloading', 'preparing', 'queued'):
                return {'removed': False, 'note': '已请求取消；Steam 下载完成后再清理。'}
            if self.active.get(job_id, 0):
                raise ValueError('正在向手机发送，暂不能删除。')
            note = self._cleanup_source(job)
            self._remove(job)
            return {'removed': True, 'note': note}

    def _remove(self, job):
        directory = self.root / job['id']
        if directory.resolve().parent != self.root or directory.is_symlink() or directory.is_junction():
            raise ValueError('任务目录不安全，已停止清理。')
        shutil.rmtree(directory)
        del self.jobs[job['id']]

    def begin_transfer(self, job_id):
        with self.lock:
            job = self.get(job_id)
            if job['state'] != 'ready':
                raise ValueError('视频尚未准备完成。')
            self.active[job_id] = self.active.get(job_id, 0) + 1
            return self.root / job_id / 'video', self.public(job)

    def end_transfer(self, job_id):
        with self.lock:
            self.active[job_id] = max(0, self.active.get(job_id, 1) - 1)
