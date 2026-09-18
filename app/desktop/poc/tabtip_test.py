# -*- coding: utf-8 -*-
"""触摸键盘(TabTip)端到端取证测试.

流程:
  1) 聚焦微信并点中聊天输入框(让触摸键盘有输入目标)
  2) 截图, 自动定位触摸键盘各按键的屏幕坐标
  3) 启动键盘取证子进程
  4) 用鼠标点击触摸键盘上的 m / j / 回车
  5) 等待取证结束, 打印每个按键的 flags/dwExtraInfo
  6) 读微信输入框, 判断消息是否真的发出

用法: python tabtip_test.py [文本]   默认 "mj"
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

from mjegg.im_targets import build_targets
from mjegg.config import load_config

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32
try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except Exception:
    pass


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", wt.LONG), ("dy", wt.LONG), ("mouseData", wt.DWORD),
                ("dwFlags", wt.DWORD), ("time", wt.DWORD),
                ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]


class _U(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT)]


class INPUT(ctypes.Structure):
    _fields_ = [("type", wt.DWORD), ("u", _U)]


def click(x, y):
    user32.SetCursorPos(x, y)
    time.sleep(0.2)
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
        a = (rc.right - rc.left) * (rc.bottom - rc.top)
        if a > min_area:
            res.append((hwnd, a))
        return True
    user32.EnumWindows(EP(cb), 0)
    return max(res, key=lambda x: x[1])[0] if res else 0


def detect_keys(im, y, x0=700, x1=1240, min_w=25, thresh=52):
    """在一行里按键帽色块找按键中心."""
    runs = []
    start = None
    for x in range(x0, x1):
        c = im.getpixel((x, y))
        light = c[0] > thresh and c[1] > thresh and c[2] > thresh
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
    text = sys.argv[1] if len(sys.argv) > 1 else "mj"

    # ---- 1) 聚焦微信 + 点中输入框 ----
    hwnd = main_window_of("Weixin.exe")
    if not hwnd:
        print("微信未运行")
        return
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.9)
    cfg = load_config()
    tgt = [t for t in build_targets(cfg["targets"]) if t.process == "Weixin.exe"][0]
    node = tgt.locate(hwnd)
    r = node.BoundingRectangle
    print("微信输入框 rect=(%d,%d,%d,%d) 当前=%r" % (r.left, r.top, r.right, r.bottom,
                                                 tgt.read_text(node)))
    click((r.left + r.right) // 2, (r.top + r.bottom) // 2)
    time.sleep(1.2)
    print("前台窗口 = %s (微信=%s)" % (user32.GetForegroundWindow(), hwnd))

    # ---- 2) 从任务栏托盘打开触摸键盘(必须后开, 否则切换焦点会把它关掉) ----
    TRAY_KB = (1555, 1048)
    print("点击托盘触摸键盘图标 @ %s" % (TRAY_KB,))
    click(*TRAY_KB)
    time.sleep(3.5)
    print("前台窗口(开键盘后) = %s" % user32.GetForegroundWindow())

    img = ImageGrab.grab()
    img.save(os.path.join(ROOT, "poc", "tabtip_live.png"))
    img = img.convert("RGB")
    rows = {}
    for name, y in (("number", 762), ("qwerty", 780), ("home", 846),
                    ("shift", 912), ("bottom", 976)):
        rows[name] = detect_keys(img, y)
    for name, y in (("number", 762), ("qwerty", 780), ("home", 846),
                    ("shift", 912), ("bottom", 976)):
        print("%-8s y=%d 键中心: %s" % (name, y, rows[name]))

    # m 在 shift 行第 7 个; j 在 home 行第 7 个; 回车在 bottom 行最后一个
    shift_xs, home_xs, bottom_xs = rows["shift"], rows["home"], rows["bottom"]
    if len(shift_xs) < 7 or len(home_xs) < 7 or not bottom_xs:
        print("按键定位失败, 中止")
        return
    key_m = (shift_xs[6], 912)
    key_j = (home_xs[6], 846)
    key_enter = (bottom_xs[-1], 976)
    print("将点击: m=%s j=%s enter=%s" % (key_m, key_j, key_enter))

    # ---- 3) 启动键盘取证 ----
    logp = subprocess.Popen([sys.executable, os.path.join(ROOT, "poc", "keylog.py"), "22"],
                            cwd=os.path.join(ROOT, "poc"),
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2.0)

    # ---- 4) 点击触摸键盘 ----
    for label, (kx, ky) in (("m", key_m), ("j", key_j), ("Enter", key_enter)):
        print("点击触摸键盘 %s @ (%d,%d)" % (label, kx, ky))
        click(kx, ky)
        time.sleep(0.5)

    logp.wait(timeout=40)
    time.sleep(0.6)

    # ---- 5) 打印取证结果 ----
    p = os.path.join(ROOT, "poc", "keylog.out.txt")
    print("\n--- 键盘取证 ---")
    if os.path.exists(p):
        with open(p, encoding="utf-8") as f:
            for ln in f:
                print("  " + ln.rstrip())

    # ---- 6) 微信侧状态 ----
    time.sleep(1.0)
    node = tgt.locate(hwnd, force=True)
    print("\n微信输入框现在 = %r" % tgt.read_text(node))
    shot = os.path.join(ROOT, "poc", "tabtip_result.png")
    ImageGrab.grab().save(shot)
    print("截图 %s" % shot)


if __name__ == "__main__":
    main()
