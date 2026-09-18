# -*- coding: utf-8 -*-
"""验证: 输入框 UIA 节点在文本变化后是否会失效(缓存节点能否复用).

用法: python test_stale.py [进程名] [文本]
"""
import ctypes
import ctypes.wintypes as wt
import os
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from mjegg.config import load_config
from mjegg.im_targets import build_targets, find_target

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32
try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except Exception:
    pass

KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wt.WORD), ("wScan", wt.WORD), ("dwFlags", wt.DWORD),
                ("time", wt.DWORD), ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", wt.LONG), ("dy", wt.LONG), ("mouseData", wt.DWORD),
                ("dwFlags", wt.DWORD), ("time", wt.DWORD),
                ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]


class _U(ctypes.Union):
    _fields_ = [("ki", KEYBDINPUT), ("mi", MOUSEINPUT)]


class INPUT(ctypes.Structure):
    _fields_ = [("type", wt.DWORD), ("u", _U)]


def proc_name_of(pid):
    h = kernel32.OpenProcess(0x1000, False, pid)
    if not h:
        return ""
    buf = ctypes.create_unicode_buffer(1024)
    size = wt.DWORD(1024)
    nm = ""
    if kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
        nm = buf.value.split("\\")[-1]
    kernel32.CloseHandle(h)
    return nm


def main_window_of(proc):
    res = []
    EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        pid = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if proc_name_of(pid.value).lower() != proc.lower() or not user32.IsWindowVisible(hwnd):
            return True
        rc = wt.RECT()
        user32.GetWindowRect(hwnd, ctypes.byref(rc))
        a = (rc.right - rc.left) * (rc.bottom - rc.top)
        if a > 100000:
            res.append((hwnd, a))
        return True
    user32.EnumWindows(EnumProc(cb), 0)
    return max(res, key=lambda x: x[1])[0] if res else 0


def send_text(text):
    for ch in text:
        for up in (False, True):
            inp = INPUT()
            inp.type = 1
            inp.u.ki = KEYBDINPUT(0, ord(ch), KEYEVENTF_UNICODE | (KEYEVENTF_KEYUP if up else 0), 0, None)
            user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
            time.sleep(0.04)


def click(x, y):
    user32.SetCursorPos(x, y)
    time.sleep(0.2)
    for flags in (0x0002, 0x0004):
        inp = INPUT()
        inp.type = 0
        inp.u.mi = MOUSEINPUT(x, y, 0, flags, 0, None)
        user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
        time.sleep(0.06)


def main():
    proc = sys.argv[1] if len(sys.argv) > 1 else "Weixin.exe"
    text = sys.argv[2] if len(sys.argv) > 2 else "mj"
    cfg = load_config()
    targets = build_targets(cfg["targets"])
    t = find_target(proc, targets)
    hwnd = main_window_of(proc)
    print("目标=%s hwnd=%s" % (proc, hwnd))
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.8)
    print("前台窗口 = %s" % (user32.GetForegroundWindow(),))

    node_a = t.locate(hwnd)
    if node_a is None:
        print("定位失败")
        return
    print("A 节点 cls=%r 文本=%r" % (node_a.ClassName, t.read_text(node_a)))
    r = node_a.BoundingRectangle
    click((r.left + r.right) // 2, (r.top + r.bottom) // 2)
    time.sleep(0.4)
    print("前台窗口(点击后) = %s" % (user32.GetForegroundWindow(),))
    send_text(text)
    time.sleep(0.8)

    print("\n--- 用同一个缓存节点 A 读 ---")
    print("A 文本=%r" % t.read_text(node_a))
    try:
        print("A GetValuePattern().Value = %r" % (node_a.GetValuePattern().Value,))
    except Exception as e:
        print("A GetValuePattern 异常: %r" % (e,))

    print("\n--- 重新定位节点 B ---")
    t.invalidate()
    node_b = t.locate(hwnd)
    if node_b:
        print("B 节点 cls=%r 文本=%r" % (node_b.ClassName, t.read_text(node_b)))
        try:
            print("B GetValuePattern().Value = %r" % (node_b.GetValuePattern().Value,))
        except Exception as e:
            print("B GetValuePattern 异常: %r" % (e,))
    else:
        print("B 定位失败")


if __name__ == "__main__":
    main()
