# -*- coding: utf-8 -*-
"""实测: 向 QQ 输入框打字, 观察 UIA 节点如何反映"有文字/空".

用法: python qq_type_test.py [要输入的文本]
流程: 找到 QQ 主窗口 -> 置前 -> 点击输入框中心聚焦 -> 用 SendInput 逐字符输入 -> 转储节点
不会按回车, 不会发送消息.
"""
import ctypes
import ctypes.wintypes as wt
import sys
import time

import uiautomation as uia

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

INPUT_KEYBOARD = 1
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_ABSOLUTE = 0x8000


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wt.WORD), ("wScan", wt.WORD), ("dwFlags", wt.DWORD),
                ("time", wt.DWORD), ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", wt.LONG), ("dy", wt.LONG), ("mouseData", wt.DWORD),
                ("dwFlags", wt.DWORD), ("time", wt.DWORD),
                ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]


class _INPUTunion(ctypes.Union):
    _fields_ = [("ki", KEYBDINPUT), ("mi", MOUSEINPUT)]


class INPUT(ctypes.Structure):
    _fields_ = [("type", wt.DWORD), ("u", _INPUTunion)]


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


def fg_info():
    hwnd = user32.GetForegroundWindow()
    pid = wt.DWORD()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    n = ctypes.windll.user32.GetWindowTextLengthW(hwnd)
    title = ctypes.create_unicode_buffer(n + 1)
    user32.GetWindowTextW(hwnd, title, n + 1)
    return hwnd, proc_name_of(pid.value), title.value


def find_qq_main():
    res = []
    EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        pid = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if proc_name_of(pid.value).lower() != "qq.exe":
            return True
        if not user32.IsWindowVisible(hwnd):
            return True
        cls = ctypes.create_unicode_buffer(256)
        user32.GetClassNameW(hwnd, cls, 256)
        rc = wt.RECT()
        user32.GetWindowRect(hwnd, ctypes.byref(rc))
        area = (rc.right - rc.left) * (rc.bottom - rc.top)
        if area > 100000:
            res.append((hwnd, cls.value, (rc.left, rc.top, rc.right, rc.bottom), area))
        return True
    user32.EnumWindows(EnumProc(cb), 0)
    return max(res, key=lambda x: x[3]) if res else None


def send_text(text):
    for ch in text:
        for up in (False, True):
            inp = INPUT()
            inp.type = INPUT_KEYBOARD
            inp.u.ki = KEYBDINPUT(0, ord(ch), KEYEVENTF_UNICODE | (KEYEVENTF_KEYUP if up else 0), 0, None)
            user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
            time.sleep(0.03)


def click(x, y):
    sw = user32.GetSystemMetrics(0)
    sh = user32.GetSystemMetrics(1)
    user32.SetCursorPos(x, y)
    time.sleep(0.15)
    for flags in (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP):
        inp = INPUT()
        inp.type = 0
        inp.u.mi = MOUSEINPUT(x * 65535 // sw, y * 65535 // sh, 0,
                              flags | MOUSEEVENTF_ABSOLUTE, 0, None)
        user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
        time.sleep(0.05)


def find_editor(win):
    hits = []

    def walk(node, depth):
        if depth > 25 or hits:
            return
        try:
            ch = node.GetChildren()
        except Exception:
            return
        for c in ch:
            try:
                if "ExEditor-qq-msg-editor" in (c.ClassName or ""):
                    hits.append(c)
                    return
            except Exception:
                pass
            walk(c, depth + 1)
    walk(win, 1)
    return hits[0] if hits else None


def dump(ed, tag):
    print("  [%s] cls=%r" % (tag, ed.ClassName))
    try:
        print("         name=%r" % (ed.Name or ""))
    except Exception:
        pass
    try:
        lp = ed.GetLegacyIAccessiblePattern()
        print("         Legacy.Value=%r" % (lp.Value,))
    except Exception as e:
        print("         Legacy 读取失败: %s" % e)
    try:
        r = ed.BoundingRectangle
        print("         rect=(%d,%d,%d,%d)" % (r.left, r.top, r.right, r.bottom))
    except Exception:
        pass
    try:
        kids = ed.GetChildren()
        print("         子节点 %d 个:" % len(kids))
        for k in kids[:12]:
            try:
                print("           - type=%s cls=%r name=%r" % (
                    k.ControlTypeName, k.ClassName, (k.Name or "")[:40]))
            except Exception:
                pass
    except Exception as e:
        print("         子节点读取失败: %s" % e)


def main():
    text = sys.argv[1] if len(sys.argv) > 1 else "mj"
    uia.SetGlobalSearchTimeout(1.0)
    m = find_qq_main()
    if not m:
        print("QQ 主窗口未找到")
        return
    hwnd, cls, rect, _ = m
    print("QQ 主窗口 hwnd=%s cls=%r rect=%s" % (hwnd, cls, rect))
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.8)
    print("置前后前台: %s" % (fg_info(),))

    win = uia.ControlFromHandle(hwnd)
    ed = find_editor(win)
    if not ed:
        print("未找到 QQ 输入框")
        return
    dump(ed, "打字前")

    r = ed.BoundingRectangle
    cx, cy = (r.left + r.right) // 2, (r.top + r.bottom) // 2
    print("\n点击输入框中心 (%d,%d) 聚焦..." % (cx, cy))
    click(cx, cy)
    time.sleep(0.4)
    print("点击后前台: %s" % (fg_info(),))

    print("\n输入 %r ..." % text)
    send_text(text)
    time.sleep(0.8)

    win = uia.ControlFromHandle(hwnd)
    ed = find_editor(win)
    if ed:
        dump(ed, "打字后")
    else:
        print("  打字后找不到输入框了")


if __name__ == "__main__":
    main()
