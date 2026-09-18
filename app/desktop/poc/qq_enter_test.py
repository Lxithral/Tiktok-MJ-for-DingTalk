# -*- coding: utf-8 -*-
"""实测: 在 QQ 当前会话按下回车, 观察输入框与消息列表变化.

用法: python qq_enter_test.py
"""
import ctypes
import ctypes.wintypes as wt
import time

import uiautomation as uia

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

VK_RETURN = 0x0D
KEYEVENTF_KEYUP = 0x0002


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


def press_enter():
    for up in (False, True):
        inp = INPUT()
        inp.type = 1
        inp.u.ki = KEYBDINPUT(VK_RETURN, 0, KEYEVENTF_KEYUP if up else 0, 0, None)
        user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
        time.sleep(0.05)


def find_qq_main():
    res = []
    EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        pid = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if proc_name_of(pid.value).lower() != "qq.exe" or not user32.IsWindowVisible(hwnd):
            return True
        rc = wt.RECT()
        user32.GetWindowRect(hwnd, ctypes.byref(rc))
        a = (rc.right - rc.left) * (rc.bottom - rc.top)
        if a > 100000:
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


def text_of(node, depth=0, acc=None, max_depth=6):
    if acc is None:
        acc = []
    if depth > max_depth:
        return acc
    try:
        ch = node.GetChildren()
    except Exception:
        return acc
    for c in ch:
        try:
            nm = c.Name or ""
            if c.ControlTypeName == "TextControl" and nm.strip():
                acc.append(nm)
        except Exception:
            pass
        text_of(c, depth + 1, acc, max_depth)
    return acc


def editor_state(win):
    eds = find_cls(win, "ExEditor-qq-msg-editor")
    if not eds:
        return None, None
    ed = eds[0]
    cls = ed.ClassName or ""
    txt = "".join(text_of(ed)).replace("\n", "")
    return ("is-empty" in cls), txt


def last_messages(win, n=3):
    items = find_cls(win, "ml-item", stop_first=False)
    out = []
    for it in items[-n:]:
        out.append(" | ".join(text_of(it, max_depth=5)))
    return out


def main():
    uia.SetGlobalSearchTimeout(1.0)
    hwnd = find_qq_main()
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.6)
    win = uia.ControlFromHandle(hwnd)
    empty, txt = editor_state(win)
    print("发送前: 输入框 is_empty=%s 文本=%r" % (empty, txt))
    print("发送前最近消息: %s" % last_messages(win, 3))
    if not txt:
        print("输入框为空, 中止")
        return
    print("\n按回车发送...")
    press_enter()
    time.sleep(1.5)
    win = uia.ControlFromHandle(hwnd)
    empty, txt = editor_state(win)
    print("发送后: 输入框 is_empty=%s 文本=%r" % (empty, txt))
    print("发送后最近消息: %s" % last_messages(win, 3))


if __name__ == "__main__":
    main()
