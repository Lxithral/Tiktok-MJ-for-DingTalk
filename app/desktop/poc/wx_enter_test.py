# -*- coding: utf-8 -*-
"""实测: 在微信当前会话输入 mj 并回车, 观察输入框与消息列表.

用法: python wx_enter_test.py [文本]
"""
import ctypes
import ctypes.wintypes as wt
import sys
import time

import uiautomation as uia

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

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


def click(x, y):
    user32.SetCursorPos(x, y)
    time.sleep(0.2)
    for flags in (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP):
        inp = INPUT()
        inp.type = 0
        inp.u.mi = MOUSEINPUT(x, y, 0, flags, 0, None)
        user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
        time.sleep(0.06)


def find_main(proc, min_area=200000):
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
        if a > min_area:
            res.append((hwnd, a))
        return True
    user32.EnumWindows(EnumProc(cb), 0)
    return max(res, key=lambda x: x[1])[0] if res else 0


def find_cls(node, needle, depth=0, hits=None, stop_first=True):
    if hits is None:
        hits = []
    if depth > 28 or (stop_first and hits):
        return hits
    try:
        ch = node.GetChildren()
    except Exception:
        return hits
    for c in ch:
        try:
            if needle in (c.ClassName or ""):
                hits.append(c)
                if stop_first:
                    return hits
        except Exception:
            pass
        find_cls(c, needle, depth + 1, hits, stop_first)
    return hits


def read_value(ed):
    try:
        return ed.GetValuePattern().Value
    except Exception as e:
        return "<读取失败 %s>" % e


def last_texts(win, n=6):
    acc = []

    def walk(node, depth):
        if depth > 26 or len(acc) > 400:
            return
        try:
            ch = node.GetChildren()
        except Exception:
            return
        for c in ch:
            try:
                if c.ControlTypeName == "TextControl":
                    nm = (c.Name or "").strip()
                    if nm:
                        acc.append(nm)
            except Exception:
                pass
            walk(c, depth + 1)
    walk(win, 1)
    return acc[-n:]


def main():
    text = sys.argv[1] if len(sys.argv) > 1 else "mj"
    uia.SetGlobalSearchTimeout(1.0)
    hwnd = find_main("Weixin.exe")
    print("微信主窗口 hwnd=%s" % hwnd)
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.7)
    win = uia.ControlFromHandle(hwnd)
    eds = find_cls(win, "mmui::ChatInputField")
    if not eds:
        print("未找到微信聊天输入框")
        return
    ed = eds[0]
    r = ed.BoundingRectangle
    print("输入框 name=%r rect=(%d,%d,%d,%d)" % (
        ed.Name, r.left, r.top, r.right, r.bottom))
    print("发送前 value=%r" % read_value(ed))
    before = last_texts(win, 6)
    print("发送前最近文本: %s" % before)

    click((r.left + r.right) // 2, (r.top + r.bottom) // 2)
    time.sleep(0.3)
    print("\n输入 %r ..." % text)
    send_text(text)
    time.sleep(0.7)
    win = uia.ControlFromHandle(hwnd)
    eds = find_cls(win, "mmui::ChatInputField")
    print("输入后 value=%r" % read_value(eds[0]))

    print("\n按回车发送...")
    press_enter()
    time.sleep(1.5)
    win = uia.ControlFromHandle(hwnd)
    eds = find_cls(win, "mmui::ChatInputField")
    print("发送后 value=%r" % read_value(eds[0]))
    after = last_texts(win, 6)
    print("发送后最近文本: %s" % after)
    print("新增: %s" % [x for x in after if x not in before])


if __name__ == "__main__":
    main()
