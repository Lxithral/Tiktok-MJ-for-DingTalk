# -*- coding: utf-8 -*-
"""键盘事件取证: 记录每个按键的 vk/scan/flags/dwExtraInfo, 用来区分
"屏幕键盘(OSK/TabTip)注入" 与 "自动化工具注入".

用法: python keylog.py [秒数]   默认 40 秒
输出: poc/keylog.out.txt
"""
import ctypes
import ctypes.wintypes as wintypes
import sys
import time

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

WH_KEYBOARD_LL = 13
WM_KEYDOWN = 0x0100
WM_SYSKEYDOWN = 0x0104
LLKHF_EXTENDED = 0x01
LLKHF_INJECTED = 0x10
LLKHF_ALTDOWN = 0x20
LLKHF_UP = 0x80

HOOKPROC = ctypes.WINFUNCTYPE(ctypes.c_ssize_t, ctypes.c_int, wintypes.WPARAM, wintypes.LPARAM)


class KBDLLHOOKSTRUCT(ctypes.Structure):
    _fields_ = [("vkCode", wintypes.DWORD), ("scanCode", wintypes.DWORD),
                ("flags", wintypes.DWORD), ("time", wintypes.DWORD),
                ("dwExtraInfo", ctypes.c_size_t)]


user32.CallNextHookEx.argtypes = [ctypes.c_void_p, ctypes.c_int, wintypes.WPARAM, wintypes.LPARAM]
user32.CallNextHookEx.restype = ctypes.c_ssize_t
user32.SetWindowsHookExW.argtypes = [ctypes.c_int, HOOKPROC, ctypes.c_void_p, wintypes.DWORD]
user32.SetWindowsHookExW.restype = ctypes.c_void_p
user32.UnhookWindowsHookEx.argtypes = [ctypes.c_void_p]

lines = []
start = time.time()
DURATION = float(sys.argv[1]) if len(sys.argv) > 1 else 40.0


def fg_class():
    h = user32.GetForegroundWindow()
    if not h:
        return "?"
    buf = ctypes.create_unicode_buffer(256)
    user32.GetClassNameW(h, buf, 256)
    return buf.value


def cb(n_code, w_param, l_param):
    if n_code >= 0:
        info = ctypes.cast(ctypes.c_void_p(l_param), ctypes.POINTER(KBDLLHOOKSTRUCT)).contents
        down = w_param in (WM_KEYDOWN, WM_SYSKEYDOWN)
        if down:
            inj = bool(info.flags & LLKHF_INJECTED)
            lines.append("t=%5.2f vk=0x%02X scan=0x%02X flags=0x%02X%s%s%s extra=0x%X fg=%s" % (
                time.time() - start, info.vkCode, info.scanCode, info.flags,
                " INJ" if inj else "",
                " ALT" if info.flags & LLKHF_ALTDOWN else "",
                " EXT" if info.flags & LLKHF_EXTENDED else "",
                info.dwExtraInfo, fg_class()))
    return user32.CallNextHookEx(None, n_code, w_param, l_param)


def main():
    proc = HOOKPROC(cb)
    hook = user32.SetWindowsHookExW(WH_KEYBOARD_LL, proc, None, 0)
    if not hook:
        print("hook 失败 err=%s" % kernel32.GetLastError())
        return
    print("键盘取证中, %.0f 秒... (请在屏幕键盘上敲 m j 回车)" % DURATION)
    msg = wintypes.MSG()
    end = time.time() + DURATION
    while time.time() < end:
        # 用 PeekMessage 轮询, 保证能按时退出
        while user32.PeekMessageW(ctypes.byref(msg), None, 0, 0, 1):
            user32.TranslateMessage(ctypes.byref(msg))
            user32.DispatchMessageW(ctypes.byref(msg))
        time.sleep(0.02)
    user32.UnhookWindowsHookEx(hook)
    with open("keylog.out.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("已记录 %d 条按键事件 -> keylog.out.txt" % len(lines))
    for ln in lines[-40:]:
        print("  " + ln)


if __name__ == "__main__":
    main()
