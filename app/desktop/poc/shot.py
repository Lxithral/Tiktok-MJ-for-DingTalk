# -*- coding: utf-8 -*-
"""窗口截图工具: 按进程名/窗口标题截取某个窗口, 存成 PNG.

用法:
  python shot.py <进程名> [输出路径] [--full]
  python shot.py QQ.exe poc/shot_qq.png
  python shot.py --list          # 列出所有可见窗口
"""
import ctypes
import ctypes.wintypes as wt
import sys

from PIL import ImageGrab

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32


def make_dpi_aware():
    """必须: 否则 GetWindowRect/截图拿到的是缩放后的逻辑坐标, 与物理像素错位."""
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)  # PER_MONITOR_AWARE
    except Exception:
        try:
            user32.SetProcessDPIAware()
        except Exception:
            pass


make_dpi_aware()


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


def list_windows():
    out = []
    EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        if not user32.IsWindowVisible(hwnd):
            return True
        rc = wt.RECT()
        user32.GetWindowRect(hwnd, ctypes.byref(rc))
        if rc.right - rc.left < 50:
            return True
        pid = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        n = user32.GetWindowTextLengthW(hwnd)
        t = ctypes.create_unicode_buffer(n + 1)
        user32.GetWindowTextW(hwnd, t, n + 1)
        out.append((hwnd, proc_name_of(pid.value), t.value,
                    (rc.left, rc.top, rc.right, rc.bottom)))
        return True
    user32.EnumWindows(EnumProc(cb), 0)
    return out


def pick(proc):
    best = None
    for hwnd, pname, title, rc in list_windows():
        if pname.lower() != proc.lower():
            continue
        area = (rc[2] - rc[0]) * (rc[3] - rc[1])
        if best is None or area > best[0]:
            best = (area, hwnd, title, rc)
    return best


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--list":
        for hwnd, pname, title, rc in sorted(list_windows(), key=lambda x: x[1]):
            print("%-22s %-14s %-30s %s" % (pname, hwnd, title[:30], rc))
        return
    proc = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else "shot_%s.png" % proc.split(".")[0]
    full = "--full" in sys.argv
    if full:
        img = ImageGrab.grab()
        img.save(out)
        print("已保存全屏 %s %s" % (out, img.size))
        return
    b = pick(proc)
    if not b:
        print("未找到进程 %s 的可见窗口" % proc)
        return
    area, hwnd, title, rc = b
    # 裁剪时向内收一点, 避开窗口阴影
    img = ImageGrab.grab(bbox=(max(0, rc[0]), max(0, rc[1]), rc[2], rc[3]))
    img.save(out)
    print("已保存 %s  hwnd=%s title=%r rect=%s size=%s" % (out, hwnd, title, rc, img.size))


if __name__ == "__main__":
    main()
