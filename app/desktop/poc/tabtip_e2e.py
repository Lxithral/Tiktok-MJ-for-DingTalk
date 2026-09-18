# -*- coding: utf-8 -*-
"""触摸键盘真实端到端测试: 用触摸键盘在微信里敲 mj 并发送, 看彩蛋是否播放.

前提: 彩蛋程序已在运行(源码或 exe).
流程: 聚焦微信 -> 点输入框 -> 托盘开触摸键盘 -> 点 m / j / 回车 / 回车 -> 看日志

用法: python tabtip_e2e.py
"""
import ctypes
import ctypes.wintypes as wt
import os
import sys
import time

from PIL import Image, ImageGrab

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from mjegg.config import load_config
from mjegg.im_targets import build_targets

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32
try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except Exception:
    pass

TRAY_KB = (1555, 1048)


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


def press_backspace(n=20):
    for _ in range(n):
        for up in (False, True):
            i = INPUT()
            i.type = 1
            i.u.ki = KEYBDINPUT(0x08, 0, 0x0002 if up else 0, 0, None)
            user32.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))
            time.sleep(0.02)


def click(x, y, hold=0.08):
    user32.SetCursorPos(x, y)
    time.sleep(0.18)
    for f in (0x0002, 0x0004):
        i = INPUT()
        i.type = 0
        i.u.mi = MOUSEINPUT(x, y, 0, f, 0, None)
        user32.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))
        time.sleep(hold)


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


def main_window_of(proc, min_area=100000):
    res = []
    EP = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        pid = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if proc_name_of(pid.value).lower() != proc.lower() or not user32.IsWindowVisible(hwnd):
            return True
        rc = wt.RECT()
        user32.GetWindowRect(hwnd, ctypes.byref(rc))
        a = (rc.right - rc.left) * (rc.bottom - rc.top)
        if a > min_area:
            res.append((hwnd, a))
        return True
    user32.EnumWindows(EP(cb), 0)
    return max(res, key=lambda x: x[1])[0] if res else 0


def detect_keys(im, y, x0=700, x1=1240, min_w=25, thresh=46):
    runs, start = [], None
    for x in range(x0, x1):
        c = im.getpixel((x, y))
        light = max(c) > thresh
        if light and start is None:
            start = x
        elif not light and start is not None:
            if x - start >= min_w:
                runs.append((start + x - 1) // 2)
            start = None
    if start is not None and x1 - start >= min_w:
        runs.append((start + x1 - 1) // 2)
    return runs


def log_tail(n=10):
    p = os.path.join(ROOT, "dist", "mj-dingtalk.log")
    if not os.path.exists(p):
        p = os.path.join(ROOT, "mj-dingtalk.log")
    if not os.path.exists(p):
        return "(无日志)"
    with open(p, encoding="utf-8", errors="replace") as f:
        return "".join(f.readlines()[-n:]).rstrip()


def main():
    hwnd = main_window_of("Weixin.exe")
    if not hwnd:
        print("微信未运行")
        return
    cfg = load_config()
    tgt = [t for t in build_targets(cfg["targets"]) if t.process == "Weixin.exe"][0]

    user32.SetForegroundWindow(hwnd)
    time.sleep(0.9)
    node = tgt.locate(hwnd)
    r = node.BoundingRectangle
    click((r.left + r.right) // 2, (r.top + r.bottom) // 2)
    time.sleep(0.6)
    # 清空输入框(退格键, 与钩子无关)
    press_backspace()
    time.sleep(0.4)
    print("微信输入框 = %r" % tgt.read_text(node))

    print("打开触摸键盘...")
    click(*TRAY_KB)
    time.sleep(3.5)
    img = ImageGrab.grab().convert("RGB")
    img.save(os.path.join(ROOT, "poc", "tabtip_e2e.png"))
    qwerty = detect_keys(img, 780)
    print("qwerty 行(10 键): %s" % qwerty)
    if len(qwerty) < 10:
        print("qwerty 行定位失败")
        return
    # 用 qwerty 行(q..p 十键)标定键距与偏移, 再推算其他行 —— 比按行内序号取键稳得多
    q0 = qwerty[0]
    pitch = (qwerty[9] - qwerty[0]) / 9.0
    key_m = (int(q0 + 7.0 * pitch), 912)      # 第四行第 8 键 = m
    key_j = (int(q0 + 6.5 * pitch), 846)      # 第三行第 7 键 = j
    key_enter = (int(q0 + 9.0 * pitch), 976)  # 末行最右 = 回车
    print("键距=%.1f  q0=%d  ->  m=%s j=%s enter=%s" % (pitch, q0, key_m, key_j, key_enter))

    print("\n--- 发送前日志 ---")
    print(log_tail(4))

    for label, k in (("m", key_m), ("j", key_j)):
        print("点触摸键盘 %s" % label)
        click(*k)
        time.sleep(0.6)
    time.sleep(0.8)
    print("输入框(上屏前) = %r" % tgt.read_text(node))

    print("点回车(提交候选词)...")
    click(*key_enter)
    time.sleep(1.0)
    print("输入框(提交后) = %r" % tgt.read_text(node))

    print("再点回车(发送)...")
    click(*key_enter)
    time.sleep(1.5)
    print("输入框(发送后) = %r" % tgt.read_text(node))

    time.sleep(1.5)
    print("\n--- 发送后日志 ---")
    print(log_tail(12))
    ImageGrab.grab().save(os.path.join(ROOT, "poc", "tabtip_e2e_after.png"))


if __name__ == "__main__":
    main()
