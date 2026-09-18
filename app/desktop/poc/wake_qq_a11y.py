# -*- coding: utf-8 -*-
"""唤醒 Electron/Chromium 应用(QQ NT)的无障碍树, 并转储其 UIA 结构.

原理: Chromium 只在检测到 UIA 客户端(WB_GETOBJECT/WM_GETOBJECT)时才构建 a11y 树.
这里向 Chrome_RenderWidgetHostHWND 发送 WM_GETOBJECT(OBJID_CLIENT) 作为"敲醒"信号,
等待渲染进程构建完树后再遍历.

用法: python wake_qq_a11y.py [进程名]   (默认 QQ.exe)
"""
import ctypes
import ctypes.wintypes as wt
import io
import sys
import time

import uiautomation as uia

WM_GETOBJECT = 0x003D
OBJID_CLIENT = 0xFFFFFFFC
out = io.StringIO()


def w(s=""):
    print(s)
    out.write(s + "\n")


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


def enum_windows():
    """返回 [(hwnd, pid, cls, title, rect)] 所有顶层窗口."""
    res = []
    EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        pid = wt.DWORD()
        ctypes.windll.user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        cls = ctypes.create_unicode_buffer(256)
        ctypes.windll.user32.GetClassNameW(hwnd, cls, 256)
        n = ctypes.windll.user32.GetWindowTextLengthW(hwnd)
        title = ctypes.create_unicode_buffer(n + 1)
        ctypes.windll.user32.GetWindowTextW(hwnd, title, n + 1)
        rc = wt.RECT()
        ctypes.windll.user32.GetWindowRect(hwnd, ctypes.byref(rc))
        vis = ctypes.windll.user32.IsWindowVisible(hwnd)
        res.append((hwnd, pid.value, cls.value, title.value,
                    (rc.left, rc.top, rc.right, rc.bottom), bool(vis)))
        return True

    ctypes.windll.user32.EnumWindows(EnumProc(cb), 0)
    return res


def main():
    proc = sys.argv[1] if len(sys.argv) > 1 else "QQ.exe"
    uia.SetGlobalSearchTimeout(0.6)
    wins = [x for x in enum_windows() if proc_name_of(x[1]).lower() == proc.lower()]
    w("进程 %s: %d 个顶层窗口" % (proc, len(wins)))
    for hwnd, pid, cls, title, rc, vis in wins:
        w("  hwnd=%s pid=%d cls=%r title=%r rect=%s visible=%s"
          % (hwnd, pid, cls, title[:40], rc, vis))

    # 敲醒所有 render widget
    nudge = [x for x in wins if "Chrome_RenderWidgetHostHWND" in x[2]
             or "Chrome_WidgetWin" in x[2]]
    w("\n向 %d 个 Chromium 窗口发送 WM_GETOBJECT 唤醒信号..." % len(nudge))
    for hwnd, pid, cls, title, rc, vis in nudge:
        ctypes.windll.user32.SendMessageW(hwnd, WM_GETOBJECT, 0, OBJID_CLIENT)
        w("  -> hwnd=%s cls=%r" % (hwnd, cls))

    # 递归找子窗口里的 render host
    def find_children(parent):
        found = []
        EnumChild = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

        def cb(hwnd, _):
            cls = ctypes.create_unicode_buffer(256)
            ctypes.windll.user32.GetClassNameW(hwnd, cls, 256)
            found.append((hwnd, cls.value))
            return True
        ctypes.windll.user32.EnumChildWindows(parent, EnumChild(cb), 0)
        return found

    for hwnd, pid, cls, title, rc, vis in wins:
        for ch, ccls in find_children(hwnd):
            if "Chrome_RenderWidgetHostHWND" in ccls:
                ctypes.windll.user32.SendMessageW(ch, WM_GETOBJECT, 0, OBJID_CLIENT)
                w("  -> 子窗口 hwnd=%s cls=%r" % (ch, ccls))

    w("\n等待渲染进程构建 a11y 树 (4 秒)...")
    time.sleep(4)

    root = uia.GetRootControl()
    stats = {"n": 0, "edits": []}

    def walk(node, depth, lines, limit):
        if depth > 22 or len(lines) >= limit:
            return
        try:
            children = node.GetChildren()
        except Exception:
            return
        for ch in children:
            if len(lines) >= limit:
                return
            stats["n"] += 1
            try:
                ct = ch.ControlTypeName.replace("Control", "")
                c2 = ch.ClassName or ""
                nm = (ch.Name or "").replace("\n", "\\n")[:50]
                r = ch.BoundingRectangle
                rect = "(%d,%d,%d,%d)" % (r.left, r.top, r.right, r.bottom)
            except Exception:
                continue
            mk = ""
            if "Edit" in ct or "Document" in ct or "Edit" in c2:
                mk = "   <<< EDIT"
                stats["edits"].append((depth, ct, c2, nm, rect))
            lines.append("%s%s cls=%r name=%r %s%s" % ("  " * depth, ct, c2, nm, rect, mk))
            walk(ch, depth + 1, lines, limit)

    for hwnd, pid, cls, title, rc, vis in wins:
        w("\n### hwnd=%s cls=%r title=%r" % (hwnd, cls, title[:40]))
        try:
            win = uia.ControlFromHandle(hwnd)
        except Exception as e:
            w("  ControlFromHandle 失败: %s" % e)
            continue
        lines = []
        walk(win, 1, lines, 500)
        for ln in lines:
            w(ln)
    w("\n节点总数=%d, EDIT 候选=%d" % (stats["n"], len(stats["edits"])))
    for e in stats["edits"]:
        w("  EDIT depth=%d type=%s cls=%r name=%r rect=%s" % e)
    with open("wake_qq_a11y.out.txt", "w", encoding="utf-8") as f:
        f.write(out.getvalue())


if __name__ == "__main__":
    main()
