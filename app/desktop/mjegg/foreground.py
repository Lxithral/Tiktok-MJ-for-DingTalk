# -*- coding: utf-8 -*-
"""前台窗口进程判断 (ctypes, 无第三方依赖)."""
import ctypes
import ctypes.wintypes as wintypes
import os

_user32 = ctypes.windll.user32
_kernel32 = ctypes.windll.kernel32

PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
_pid_cache = {}


def get_foreground_info():
    """返回 (进程名或None, 前台窗口句柄, 窗口rect或None)."""
    hwnd = _user32.GetForegroundWindow()
    if not hwnd:
        return None, 0, None
    pid = wintypes.DWORD(0)
    _user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    name = _pid_cache.get(pid.value)
    if name is None:
        name = ""
        h = _kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid.value)
        if h:
            buf = ctypes.create_unicode_buffer(1024)
            size = wintypes.DWORD(1024)
            if _kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
                name = os.path.basename(buf.value)
            _kernel32.CloseHandle(h)
        if len(_pid_cache) > 128:
            _pid_cache.clear()
        _pid_cache[pid.value] = name
    rect = None
    try:
        rc = wintypes.RECT()
        if _user32.GetWindowRect(hwnd, ctypes.byref(rc)):
            rect = (rc.left, rc.top, rc.right, rc.bottom)
    except Exception:
        pass
    return (name or None), hwnd, rect


def is_foreground_process(names) -> bool:
    name, _, _ = get_foreground_info()
    if not name:
        return False
    return name.lower() in {n.lower() for n in names}


def processes_present(names, require_visible=True) -> bool:
    """是否有指定进程名的窗口存在.

    用于判断"屏幕键盘是否正开着"这类环境状态. 走 EnumWindows, 结果不缓存,
    调用方自行按需限频(每 1~2 秒一次即可).
    """
    wanted = {n.lower() for n in names if n}
    if not wanted:
        return False
    found = [False]
    EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wintypes.HWND, wintypes.LPARAM)

    def _cb(hwnd, _):
        if found[0]:
            return False
        if require_visible and not _user32.IsWindowVisible(hwnd):
            return True
        pid = wintypes.DWORD(0)
        _user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if not pid.value:
            return True
        name = _pid_cache.get(pid.value)
        if name is None:
            name = ""
            h = _kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid.value)
            if h:
                buf = ctypes.create_unicode_buffer(1024)
                size = wintypes.DWORD(1024)
                if _kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
                    name = os.path.basename(buf.value)
                _kernel32.CloseHandle(h)
            if len(_pid_cache) > 128:
                _pid_cache.clear()
            _pid_cache[pid.value] = name
        if name and name.lower() in wanted:
            found[0] = True
            return False
        return True

    try:
        _user32.EnumWindows(EnumProc(_cb), 0)
    except Exception:
        return False
    return found[0]
