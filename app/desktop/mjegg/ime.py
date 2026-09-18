# -*- coding: utf-8 -*-
"""输入法(IME)组合态检测.

用途: 区分"回车是在提交候选词"还是"回车是在发送消息".

中文输入法下输入 `mj` 再按回车, 只是把字母**上屏**进输入框(消息并未发出);
此时按发送键那一瞬间 IME 处于**组合态**(有未上屏的候选串), 可据此判定为"提交",
而不是"发送"。英文输入模式或已上屏后再按回车时无组合态, 视为真正的发送。

只做只读查询, 不改变输入法状态。
"""
import ctypes
import ctypes.wintypes as wintypes

imm32 = ctypes.windll.imm32
user32 = ctypes.windll.user32

GCS_COMPSTR = 0x0008
GCS_RESULTSTR = 0x0800

imm32.ImmGetContext.argtypes = [wintypes.HWND]
imm32.ImmGetContext.restype = ctypes.c_void_p
imm32.ImmReleaseContext.argtypes = [wintypes.HWND, ctypes.c_void_p]
imm32.ImmReleaseContext.restype = wintypes.BOOL
imm32.ImmGetCompositionStringW.argtypes = [
    ctypes.c_void_p, wintypes.DWORD, ctypes.c_void_p, wintypes.DWORD]
imm32.ImmGetCompositionStringW.restype = ctypes.c_long


class GUITHREADINFO(ctypes.Structure):
    _fields_ = [
        ("cbSize", wintypes.DWORD),
        ("flags", wintypes.DWORD),
        ("hwndActive", wintypes.HWND),
        ("hwndFocus", wintypes.HWND),
        ("hwndCapture", wintypes.HWND),
        ("hwndMenuOwner", wintypes.HWND),
        ("hwndMoveSize", wintypes.HWND),
        ("hwndCaret", wintypes.HWND),
        ("rcCaret", wintypes.RECT),
    ]


def focused_hwnd(top_hwnd=None):
    """取指定(默认前台)窗口所属线程的键盘焦点窗口."""
    top = top_hwnd or user32.GetForegroundWindow()
    if not top:
        return 0
    tid = user32.GetWindowThreadProcessId(top, None)
    if not tid:
        return top
    info = GUITHREADINFO()
    info.cbSize = ctypes.sizeof(GUITHREADINFO)
    if user32.GetGUIThreadInfo(tid, ctypes.byref(info)) and info.hwndFocus:
        return info.hwndFocus
    return top


def composition_length(top_hwnd=None) -> int:
    """返回当前 IME 组合串长度(字符数); 无组合态返回 0."""
    hwnd = focused_hwnd(top_hwnd)
    if not hwnd:
        return 0
    himc = imm32.ImmGetContext(hwnd)
    if not himc:
        return 0
    try:
        n = imm32.ImmGetCompositionStringW(himc, GCS_COMPSTR, None, 0)
        return max(0, int(n)) // 2  # 返回的是字节数(W 版)
    except Exception:
        return 0
    finally:
        try:
            imm32.ImmReleaseContext(hwnd, himc)
        except Exception:
            pass


def has_composition(top_hwnd=None) -> bool:
    """当前是否处于输入法组合态(有未上屏候选)."""
    return composition_length(top_hwnd) > 0
