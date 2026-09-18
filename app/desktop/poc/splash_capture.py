# -*- coding: utf-8 -*-
"""启动应用并在 splash 显示期间连拍, 用于核对 splash 渲染.

用法: python splash_capture.py
"""
import ctypes
import os
import subprocess
import sys
import time

from PIL import ImageGrab

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PY = sys.executable
OUT = os.path.join(ROOT, "poc")

try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except Exception:
    pass

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32


def kill_existing():
    # 按进程名杀掉已运行的 python 实例(仅限本项目的 main.py)
    import ctypes.wintypes as wt
    killed = []
    EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        pid = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        return True
    # 直接用 taskkill 更简单
    subprocess.run("taskkill /F /IM MJDingTalk.exe", shell=True,
                   capture_output=True)
    return killed


def main():
    kill_existing()
    time.sleep(0.5)
    env = dict(os.environ)
    proc = subprocess.Popen([PY, "main.py"], cwd=ROOT, env=env,
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print("已启动 pid=%s" % proc.pid)
    shots = []
    t0 = time.time()
    for i in range(10):
        time.sleep(0.45)
        path = os.path.join(OUT, "splash_%02d.png" % i)
        img = ImageGrab.grab()
        img.save(path)
        # 判断屏幕中央是否有卡片(取样中心像素)
        px = img.getpixel((img.width // 2, img.height // 2))
        print("  t=%.2fs %s 中心像素=%s" % (time.time() - t0, path, px))
        shots.append(path)
    print("应用仍在运行, pid=%s (测试完请手动结束)" % proc.pid)


if __name__ == "__main__":
    main()
