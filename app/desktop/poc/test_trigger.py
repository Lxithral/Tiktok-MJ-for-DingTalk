# -*- coding: utf-8 -*-
"""端到端触发测试: 在指定 IM 里真的发送 mj, 观察彩蛋是否播放.

用法: python test_trigger.py Weixin.exe [文本]
会: 置前该应用 -> 点击输入框 -> 逐字符输入 -> 回车 -> 截图 -> 打印日志尾部
"""
import ctypes
import ctypes.wintypes as wt
import os
import sys
import time

from PIL import ImageGrab

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

VK_RETURN = 0x0D
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004


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


def press_enter():
    for up in (False, True):
        inp = INPUT()
        inp.type = 1
        inp.u.ki = KEYBDINPUT(VK_RETURN, 0, KEYEVENTF_KEYUP if up else 0, 0, None)
        user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
        time.sleep(0.05)


def press_backspace(n=16):
    for _ in range(n):
        for up in (False, True):
            inp = INPUT()
            inp.type = 1
            inp.u.ki = KEYBDINPUT(0x08, 0, KEYEVENTF_KEYUP if up else 0, 0, None)
            user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
            time.sleep(0.02)


def click(x, y):
    user32.SetCursorPos(x, y)
    time.sleep(0.2)
    for flags in (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP):
        inp = INPUT()
        inp.type = 0
        inp.u.mi = MOUSEINPUT(x, y, 0, flags, 0, None)
        user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
        time.sleep(0.06)


def log_tail(n=12):
    p = os.path.join(ROOT, "mj-dingtalk.log")
    if not os.path.exists(p):
        return "(无日志)"
    with open(p, "r", encoding="utf-8", errors="replace") as f:
        return "".join(f.readlines()[-n:]).rstrip()


def main():
    proc = sys.argv[1]
    text = sys.argv[2] if len(sys.argv) > 2 else "mj"
    cfg = load_config()
    targets = build_targets(cfg["targets"])
    t = find_target(proc, targets)
    if t is None:
        print("无该进程的适配器: %s" % proc)
        return
    hwnd = main_window_of(proc)
    if not hwnd:
        print("进程 %s 无可见主窗口" % proc)
        return
    print("目标 %s hwnd=%s" % (proc, hwnd))
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.8)

    node = t.locate(hwnd)
    if node is None:
        print("定位输入框失败")
        return
    r = node.BoundingRectangle
    print("输入框 rect=(%d,%d,%d,%d) 当前文本=%r" % (
        r.left, r.top, r.right, r.bottom, t.read_text(node)))

    cx, cy = (r.left + r.right) // 2, (r.top + r.bottom) // 2
    click(cx, cy)
    time.sleep(0.4)
    press_backspace()
    time.sleep(0.3)
    print("清空后文本=%r" % t.read_text(node))
    print("输入 %r ..." % text)
    send_text(text)
    time.sleep(0.7)
    print("输入后文本=%r" % t.read_text(node))

    print("--- 发送前日志尾部 ---")
    print(log_tail(6))

    press_enter()
    print("\n回车已按下, 等待彩蛋...")
    time.sleep(1.2)
    shot = os.path.join(ROOT, "poc", "trigger_%s.png" % proc.split(".")[0])
    ImageGrab.grab().save(shot)
    print("已截图 %s" % shot)
    time.sleep(1.5)
    print("--- 发送后日志尾部 ---")
    print(log_tail(14))


if __name__ == "__main__":
    main()
