# -*- coding: utf-8 -*-
"""验证 im_targets 适配器: 在真实运行中的微信/QQ 上定位并读取输入框.

用法: python test_targets.py
"""
import sys
import os
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import ctypes
import ctypes.wintypes as wt

from mjegg.config import load_config
from mjegg.foreground import get_foreground_info
from mjegg.im_targets import build_targets, find_target
from mjegg import ime

user32 = ctypes.windll.user32


def proc_name_of(pid):
    h = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)
    if not h:
        return ""
    buf = ctypes.create_unicode_buffer(1024)
    size = wt.DWORD(1024)
    nm = ""
    if ctypes.windll.kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
        nm = buf.value.split("\\")[-1]
    ctypes.windll.kernel32.CloseHandle(h)
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


def main():
    cfg = load_config()
    print("配置 targets:")
    for t in cfg["targets"]:
        print("   %s" % t)
    targets = build_targets(cfg["targets"])
    print("\n适配器 %d 个: %s" % (len(targets), [t.process for t in targets]))

    for proc in ("Weixin.exe", "QQ.exe", "DingTalk.exe"):
        t = find_target(proc, targets)
        if t is None:
            print("\n[%s] 无适配器" % proc)
            continue
        hwnd = main_window_of(proc)
        if not hwnd:
            print("\n[%s] 进程未运行(无可见主窗口)" % proc)
            continue
        t0 = time.time()
        node = t.locate(hwnd)
        dt = (time.time() - t0) * 1000
        if node is None:
            print("\n[%s] 输入框定位失败 (%.0fms)" % (proc, dt))
            continue
        t1 = time.time()
        text = t.read_text(node)
        dt2 = (time.time() - t1) * 1000
        print("\n[%s] 定位成功 (%.0fms)  读取=%.0fms  文本=%r" % (proc, dt, dt2, text))
        # 连续读取耗时
        t2 = time.time()
        for _ in range(20):
            t.read_text(node)
        print("        连续 20 次读取平均 %.1fms" % ((time.time() - t2) * 100))

    print("\nIME 组合态: %s (组合串长度 %d)" % (ime.has_composition(), ime.composition_length()))
    print("前台: %s" % (get_foreground_info(),))


if __name__ == "__main__":
    main()
