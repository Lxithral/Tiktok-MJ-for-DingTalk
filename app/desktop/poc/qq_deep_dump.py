# -*- coding: utf-8 -*-
"""深度转储 QQ 输入框子树, 找出文字所在节点.

用法: python qq_deep_dump.py
"""
import ctypes
import ctypes.wintypes as wt
import time

import uiautomation as uia

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32


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


def find_editor(node, depth=0, hits=None):
    if hits is None:
        hits = []
    if depth > 25 or hits:
        return hits
    try:
        ch = node.GetChildren()
    except Exception:
        return hits
    for c in ch:
        try:
            if "ExEditor-qq-msg-editor" in (c.ClassName or ""):
                hits.append(c)
                return hits
        except Exception:
            pass
        find_editor(c, depth + 1, hits)
    return hits


def dump(node, depth, max_depth=8, max_lines=None):
    if max_lines is None:
        max_lines = [200]
    if depth > max_depth or max_lines[0] <= 0:
        return
    try:
        children = node.GetChildren()
    except Exception:
        return
    for c in children:
        max_lines[0] -= 1
        if max_lines[0] <= 0:
            return
        try:
            print("%s%s cls=%r name=%r" % (
                "  " * depth, c.ControlTypeName, c.ClassName, (c.Name or "")[:60]))
        except Exception:
            continue
        dump(c, depth + 1, max_depth, max_lines)


def main():
    uia.SetGlobalSearchTimeout(1.0)
    hwnd = find_qq_main()
    print("QQ 主窗口 hwnd=%s" % hwnd)
    win = uia.ControlFromHandle(hwnd)
    eds = find_editor(win)
    if not eds:
        print("未找到输入框")
        return
    ed = eds[0]
    print("输入框 cls=%r" % ed.ClassName)
    print("Legacy.Value=%r" % (ed.GetLegacyIAccessiblePattern().Value,))
    print("--- 子树 ---")
    dump(ed, 1)
    # 同时看消息列表最后几条
    print("\n--- 消息列表最后若干节点 ---")
    msgs = []

    def walk(node, depth):
        if depth > 25:
            return
        try:
            ch = node.GetChildren()
        except Exception:
            return
        for c in ch:
            try:
                if "chat-msg-area__vlist" in (c.ClassName or "") or "ml-area" in (c.ClassName or ""):
                    msgs.append(c)
                    return
            except Exception:
                pass
            walk(c, depth + 1)
    walk(win, 1)
    for m in msgs[:1]:
        print("  消息列表 cls=%r" % m.ClassName)
        dump(m, 1, 4, [80])


if __name__ == "__main__":
    main()
