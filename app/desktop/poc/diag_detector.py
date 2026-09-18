# -*- coding: utf-8 -*-
"""诊断: 直接在本进程内跑 Detector, 用触摸键盘驱动, 逐步打印内部状态.

不改动产品代码, 只通过读取 Detector/KeyHook 的内部字段定位问题.

用法: python diag_detector.py
"""
import ctypes
import ctypes.wintypes as wt
import logging
import os
import sys
import time

from PIL import Image, ImageGrab

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

logging.basicConfig(level=logging.DEBUG,
                    format="%(asctime)s %(levelname)s %(name)s: %(message)s")

from mjegg.config import load_config
from mjegg.im_targets import build_targets
from mjegg.watcher import Detector
from mjegg import ime

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


def click(x, y):
    user32.SetCursorPos(x, y)
    time.sleep(0.18)
    for f in (0x0002, 0x0004):
        i = INPUT()
        i.type = 0
        i.u.mi = MOUSEINPUT(x, y, 0, f, 0, None)
        user32.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))
        time.sleep(0.08)


def press_backspace(n=24):
    for _ in range(n):
        for up in (False, True):
            i = INPUT()
            i.type = 1
            i.u.ki = KEYBDINPUT(0x08, 0, 0x0002 if up else 0, 0, None)
            user32.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))
            time.sleep(0.02)


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
        if (rc.right - rc.left) * (rc.bottom - rc.top) > min_area:
            res.append((hwnd, (rc.right - rc.left) * (rc.bottom - rc.top)))
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


FIRED = []


def main():
    cfg = load_config()
    det = Detector(cfg, on_trigger=lambda: FIRED.append(time.time()))
    det.start()
    time.sleep(0.8)

    hwnd = main_window_of("Weixin.exe")
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.8)
    tgt = [t for t in build_targets(cfg["targets"]) if t.process == "Weixin.exe"][0]
    node = tgt.locate(hwnd)
    r = node.BoundingRectangle
    click((r.left + r.right) // 2, (r.top + r.bottom) // 2)
    time.sleep(0.5)
    press_backspace()
    time.sleep(0.5)

    def state(tag):
        t = det._target
        node2 = t.locate(hwnd) if t else None
        print("\n[%s] 目标=%s 前台=%s" % (
            tag, t.process if t else None, proc_name_of(
                (lambda p: (user32.GetWindowThreadProcessId(user32.GetForegroundWindow(), ctypes.byref(p)), p.value)[1])(wt.DWORD()))))
        print("     输入框文本=%r  IME组合态=%s(长度%d)" % (
            t.read_text(node2) if (t and node2) else None,
            ime.has_composition(hwnd), ime.composition_length(hwnd)))
        print("     vk_buffer=%s vk_pending=%s 队列=%d" % (
            det.hook.vk_buffer, det.hook.vk_pending, det.hook.send_events.qsize()))
        print("     last_match=%.2f last_send=%.2f cooldown=%s 已触发=%d" % (
            det._last_match_ts, det._last_send_ts, det._in_cooldown(), len(FIRED)))

    state("初始")
    print("\n打开触摸键盘...")
    click(*TRAY_KB)
    time.sleep(3.5)
    img = ImageGrab.grab().convert("RGB")
    q = detect_keys(img, 780)
    print("qwerty 行: %s" % q)
    if len(q) < 10:
        print("键盘定位失败")
        return
    pitch = (q[9] - q[0]) / 9.0
    key_m = (int(q[0] + 7.0 * pitch), 912)
    key_j = (int(q[0] + 6.5 * pitch), 846)
    key_enter = (int(q[0] + 9.0 * pitch), 976)
    key_toggle = (int(q[0] + 1.0 * pitch), 976)

    print("\n先切到英文输入(点 中/英)...")
    click(*key_toggle)
    time.sleep(0.6)
    state("切英文后")

    for label, k in (("m", key_m), ("j", key_j)):
        click(*k)
        time.sleep(0.5)
        state("点 %s 后" % label)

    print("\n点回车...")
    click(*key_enter)
    time.sleep(0.8)
    state("回车1 后")

    print("\n再点回车...")
    click(*key_enter)
    time.sleep(1.2)
    state("回车2 后")

    print("\n=== 触发次数 = %d ===" % len(FIRED))
    det.stop()


if __name__ == "__main__":
    main()
