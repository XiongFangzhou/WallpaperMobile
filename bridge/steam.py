"""Steamworks client adapter. Uses the user's installed DLL, never login cookies.

The flat API is loaded from Wallpaper Engine. All access is serialized because
Steam's callback queue is process-wide. No subscription changes are made.
"""
import ctypes as C
import os
import threading
import time
from pathlib import Path

APP_ID = 431960
U32, U64, I32, PTR = C.c_uint32, C.c_uint64, C.c_int32, C.c_void_p
INVALID = (1 << 64) - 1


class Details(C.Structure):
    _fields_ = [
        ('id', U64), ('result', I32), ('file_type', I32),
        ('creator_app', U32), ('consumer_app', U32),
        ('title', C.c_char * 129), ('description', C.c_char * 8000),
        ('owner', U64), ('created', U32), ('updated', U32), ('added', U32),
        ('visibility', I32), ('banned', C.c_bool), ('accepted', C.c_bool),
        ('truncated', C.c_bool), ('tags', C.c_char * 1025),
        ('file_handle', U64), ('preview_handle', U64),
        ('filename', C.c_char * 260), ('file_size', I32), ('preview_size', I32),
        ('url', C.c_char * 256), ('votes_up', U32), ('votes_down', U32),
        ('score', C.c_float), ('children', U32), ('total_size', U64),
    ]


class QueryResult(C.Structure):
    _fields_ = [('handle', U64), ('result', I32), ('count', U32),
                ('total', U32), ('cached', C.c_bool), ('cursor', C.c_char * 256)]


class Callback(C.Structure):
    _fields_ = [('user', I32), ('callback', I32), ('param', PTR), ('size', I32)]


class DownloadResult(C.Structure):
    _fields_ = [('app', U32), ('id', U64), ('result', I32)]


def decode(value):
    return value.split(b'\0', 1)[0].decode('utf-8', 'replace')


class SteamError(RuntimeError):
    pass


class Steam:
    SORTS = {'popular': 0, 'newest': 1, 'trending': 3,
             'votes': 10, 'relevance': 11, 'subscriptions': 12, 'updated': 19}

    def __init__(self, dll):
        self.dll_path = Path(dll).resolve()
        self.lock = threading.RLock()
        self.download_results = {}
        os.environ['SteamAppId'] = str(APP_ID)
        os.environ['SteamGameId'] = str(APP_ID)
        self.dll_directory = os.add_dll_directory(str(self.dll_path.parent))
        self.dll = C.CDLL(str(self.dll_path))
        self._functions = {}
        error = C.create_string_buffer(1024)
        result = self.fn('SteamAPI_InitFlat', I32, PTR)(error)
        if result:
            raise SteamError('Steam 初始化失败，请先登录 Steam 并确认已购买 Wallpaper Engine：' + decode(error.value))
        self.ugc = self.fn('SteamAPI_SteamUGC_v020', PTR)()
        self.utils = self.fn('SteamAPI_SteamUtils_v010', PTR)()
        if not self.ugc or not self.utils:
            raise SteamError('安装的 Steamworks 接口版本不兼容。')
        self.fn('SteamAPI_ManualDispatch_Init', None)()
        self.pipe = self.fn('SteamAPI_GetHSteamPipe', I32)()

    def fn(self, name, result, *args):
        if name not in self._functions:
            f = getattr(self.dll, name)
            f.restype, f.argtypes = result, list(args)
            self._functions[name] = f
        return self._functions[name]

    def ugc_fn(self, name, result, *args):
        f = self.fn('SteamAPI_ISteamUGC_' + name, result, PTR, *args)
        return lambda *values: f(self.ugc, *values)

    def pump(self):
        with self.lock:
            self.fn('SteamAPI_ManualDispatch_RunFrame', None, I32)(self.pipe)
            msg = Callback()
            next_callback = self.fn('SteamAPI_ManualDispatch_GetNextCallback', C.c_bool, I32, PTR)
            while next_callback(self.pipe, C.byref(msg)):
                try:
                    if msg.callback == 3406 and msg.size >= C.sizeof(DownloadResult):
                        item = C.cast(msg.param, C.POINTER(DownloadResult)).contents
                        if item.app == APP_ID:
                            self.download_results[item.id] = item.result
                finally:
                    self.fn('SteamAPI_ManualDispatch_FreeLastCallback', None, I32)(self.pipe)

    def wait_call(self, call, struct, callback, timeout=35):
        if not call:
            raise SteamError('Steam 没有接受请求，请检查网络和登录状态。')
        deadline = time.monotonic() + timeout
        failed = C.c_bool()
        completed = self.fn('SteamAPI_ISteamUtils_IsAPICallCompleted', C.c_bool, PTR, U64, PTR)
        while not completed(self.utils, call, C.byref(failed)):
            self.pump()
            if time.monotonic() >= deadline:
                raise SteamError('Steam 请求超时，请检查 Steam 网络连接后重试。')
            time.sleep(0.03)
        if failed.value:
            raise SteamError('Steam 网络请求失败。')
        output = struct()
        ok = self.fn('SteamAPI_ISteamUtils_GetAPICallResult', C.c_bool,
                     PTR, U64, PTR, I32, I32, PTR)(
                         self.utils, call, C.byref(output), C.sizeof(output), callback, C.byref(failed))
        if not ok or failed.value or output.result != 1:
            raise SteamError(f'Steam 返回错误：{output.result}。')
        return output

    def _read_query(self, handle):
        call = self.ugc_fn('SendQueryUGCRequest', U64, U64)(handle)
        response = self.wait_call(call, QueryResult, 3401)
        items = []
        for index in range(response.count):
            d = Details()
            if not self.ugc_fn('GetQueryUGCResult', C.c_bool, U64, U32, PTR)(handle, index, C.byref(d)):
                continue
            if d.result != 1 or d.consumer_app != APP_ID:
                continue
            preview = C.create_string_buffer(4096)
            self.ugc_fn('GetQueryUGCPreviewURL', C.c_bool, U64, U32, PTR, U32)(handle, index, preview, len(preview))
            items.append({'id': str(d.id), 'title': decode(d.title),
                          'description': decode(d.description), 'preview': decode(preview.value),
                          'tags': [x.strip() for x in decode(d.tags).split(',') if x.strip()],
                          'created': d.created, 'updated': d.updated,
                          'size': d.total_size or max(0, d.file_size), 'votes': d.votes_up,
                          'score': round(d.score, 3)})
        return {'items': items, 'total': response.total}

    def query(self, *, text='', sort='trending', page=1, tags=(), days=7,
              created_after=0, created_before=0):
        with self.lock:
            if sort not in self.SORTS or not 1 <= page <= 1000:
                raise ValueError('排序方式或页码无效。')
            handle = self.ugc_fn('CreateQueryAllUGCRequestPage', U64, I32, I32, U32, U32, U32)(
                self.SORTS[sort], 0, APP_ID, APP_ID, page)
            if handle == INVALID:
                raise SteamError('Steam 无法建立搜索请求。')
            try:
                self.ugc_fn('SetLanguage', C.c_bool, U64, C.c_char_p)(handle, b'schinese')
                if text:
                    self.ugc_fn('SetSearchText', C.c_bool, U64, C.c_char_p)(handle, text.encode('utf-8'))
                for tag in tags:
                    self.ugc_fn('AddRequiredTag', C.c_bool, U64, C.c_char_p)(handle, tag.encode('utf-8'))
                if sort == 'trending':
                    self.ugc_fn('SetRankedByTrendDays', C.c_bool, U64, U32)(handle, days)
                if created_after or created_before:
                    ok = self.ugc_fn('SetTimeCreatedDateRange', C.c_bool, U64, U32, U32)(
                        handle, created_after, created_before or int(time.time()))
                    if not ok:
                        raise SteamError('Steam 不支持当前时间范围。')
                result = self._read_query(handle)
                result['page'] = page
                return result
            finally:
                self.ugc_fn('ReleaseQueryUGCRequest', C.c_bool, U64)(handle)

    def details(self, item_id):
        with self.lock:
            ids = (U64 * 1)(int(item_id))
            handle = self.ugc_fn('CreateQueryUGCDetailsRequest', U64, PTR, U32)(ids, 1)
            try:
                items = self._read_query(handle)['items']
                if not items:
                    raise SteamError('作品不存在、不可访问或不属于 Wallpaper Engine。')
                return items[0]
            finally:
                self.ugc_fn('ReleaseQueryUGCRequest', C.c_bool, U64)(handle)

    def state(self, item_id):
        with self.lock:
            return self.ugc_fn('GetItemState', U32, U64)(int(item_id))

    def install_info(self, item_id):
        with self.lock:
            path, size, stamp = C.create_string_buffer(32768), U64(), U32()
            ok = self.ugc_fn('GetItemInstallInfo', C.c_bool, U64, PTR, PTR, U32, PTR)(
                int(item_id), C.byref(size), path, len(path), C.byref(stamp))
            return Path(decode(path.value)).resolve() if ok else None

    def start_download(self, item_id):
        with self.lock:
            self.download_results.pop(int(item_id), None)
            if not self.ugc_fn('DownloadItem', C.c_bool, U64, C.c_bool)(int(item_id), False):
                raise SteamError('Steam 无法开始下载，请检查登录、购买资格及作品可见性。')

    def progress(self, item_id):
        with self.lock:
            self.pump()
            done, total = U64(), U64()
            self.ugc_fn('GetItemDownloadInfo', C.c_bool, U64, PTR, PTR)(int(item_id), C.byref(done), C.byref(total))
            return done.value, total.value, self.download_results.get(int(item_id))

    def mark_unused(self, item_id):
        with self.lock:
            name = 'SteamAPI_ISteamUGC_MarkDownloadedItemAsUnused'
            if hasattr(self.dll, name):
                return self.ugc_fn('MarkDownloadedItemAsUnused', C.c_bool, U64)(int(item_id))
            return False

    def close(self):
        with self.lock:
            self.fn('SteamAPI_Shutdown', None)()
