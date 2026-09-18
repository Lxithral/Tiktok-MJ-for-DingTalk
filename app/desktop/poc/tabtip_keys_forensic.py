# -*- coding: utf-8 -*-
"""取证: 触摸键盘各键(含回车)的 vk/scan/flags/dwExtraInfo.

用法: python tabtip_keys_forensic.py
"""
import ctypes
import ctypes.wintypes as wt
import os
import subprocess
import sys
import time

from PIL import Image, ImageGrab

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

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


def press_backspace(n=24):
    for _ in range(n):
        for up in (False, True):
            i = INPUT()
            i.type = 1
            i.u.ki = KEYBDINPUT(0x08, 0, 0x0002 if up else 0, 0, None)
            user32.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))
            time.sleep(0.02)


def click(x, y):
    user32.SetCursorPos(x, y)
    time.sleep(0.18)
    for f in (0x0002, 0x0004):
        i = INPUT()
        i.type = 0
        i.u.mi = MOUSEINPUT(x, y, 0, f, 0, None)
        user32.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))
        time.sleep(0.08)


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


def main():
    hwnd = main_window_of("Weixin.exe")
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.8)
    # 点微信输入框(用 UIA 拿位置)
    from mjegg.config import load_config
    from mjegg.im_targets import build_targets
    cfg = load_config()
    tgt = [t for t in build_targets(cfg["targets"]) if t.process == "Weixin.exe"][0]
    node = tgt.locate(hwnd)
    r = node.BoundingRectangle
    click((r.left + r.right) // 2, (r.top + r.bottom) // 2)
    time.sleep(0.6)
    # 清空
    press_backspace()
    time.sleep(0.5)

    print("打开触摸键盘...")
    click(*TRAY_KB)
    time.sleep(3.5)
    img = ImageGrab.grab().convert("RGB")
    q = detect_keys(img, 780)
    if len(q) < 10:
        print("键盘未定位到:", q)
        return
    pitch = (q[9] - q[0]) / 9.0
    key_m = (int(q[0] + 7.0 * pitch), 912)
    key_j = (int(q[0] + 6.5 * pitch), 846)
    key_enter = (int(q[0] + 9.0 * pitch), 976)
    key_back = (int(q[0] + 9.0 * pitch), 912)
    key_toggle = (int(q[0] + 1.0 * pitch), 976)   # 中/英 切换
    print("m=%s j=%s enter=%s backspace=%s toggle=%s" % (key_m, key_j, key_enter, key_back, key_toggle))

    logp = subprocess.Popen([sys.executable, os.path.join(ROOT, "poc", "keylog.py"), "26"],
                            cwd=os.path.join(ROOT, "poc"),
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2.0)
    print("依次点击: 中/英切换 -> m -> j -> 回车 -> 回车 -> 退格")
    for label, k in (("toggle", key_toggle), ("m", key_m), ("j", key_j),
                     ("Enter", key_enter), ("Enter", key_enter), ("Backspace", key_back)):
        print("  点 %s @ %s" % (label, (k,)))
        click(*k)
        time.sleep(0.55)
    logp.wait(timeout=45)
    time.sleep(0.5)

    p = os.path.join(ROOT, "poc", "keylog.out.txt")
    print("\n--- 取证结果 ---")
    if os.path.exists(p):
        with open(p, encoding="utf-8") as f:
            for ln in f:
                print("  " + ln.rstrip())
    print("\n微信输入框 = %r" % tgt.read_text(tgt.locate(hwnd, force=True)))


if __name__ == "__main__":
    main()
