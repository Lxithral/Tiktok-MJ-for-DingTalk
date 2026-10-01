# -*- coding: utf-8 -*-
"""深度探测抖音 (douyin.exe): WM_GETOBJECT 唤醒 + 全量遍历 UIA 树.

用法: python probe_douyin_deep.py
结果打印并写入 probe_douyin_deep.out.txt (UTF-8).
"""
import ctypes
import ctypes.wintypes as wt
import io
import time

import uiautomation as uia

PROC = "douyin.exe"
WM_GETOBJECT = 0x003D
OBJID_CLIENT = 0xFFFFFFFC
MAX_DEPTH = 30
out = io.StringIO()
user32 = ctypes.windll.user32


def w(s=""):
    print(s)
    out.write(s + "\n")


def find_top():
    root = uia.GetRootControl()
    for win in root.GetChildren():
        try:
            if win.ClassName == "Chrome_WidgetWin_1":
                pid = win.ProcessId
                h = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)
                nm = ""
                if h:
                    buf = ctypes.create_unicode_buffer(1024)
                    size = wt.DWORD(1024)
                    if ctypes.windll.kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
                        nm = buf.value.split("\\")[-1]
                    ctypes.windll.kernel32.CloseHandle(h)
                if nm.lower() == PROC:
                    return win
        except Exception:
            continue
    return None


def walk(node, depth, budget):
    if depth > MAX_DEPTH or budget[0] <= 0:
        return
    try:
        children = node.GetChildren()
    except Exception as e:
        w("%s<GetChildren 异常: %s>" % ("  " * depth, e))
        return
    for ch in children:
        budget[0] -= 1
        if budget[0] <= 0:
            w("%s<budget 用尽>" % ("  " * depth))
            return
        try:
            ct = ch.ControlTypeName
            cls = (ch.ClassName or "")[:60]
            name = (ch.Name or "").replace("\n", " ")[:50]
            aid = (ch.AutomationId or "")[:40]
            val = ""
            if "Edit" in ct or "Document" in ct:
                try:
                    vp = ch.GetValuePattern()
                    if vp:
                        val = (vp.Value or "")[:40]
                except Exception:
                    pass
            w("%s[%s] cls=%r name=%r aid=%r%s" % (
                "  " * depth, ct, cls, name, aid,
                (" value=%r" % val) if val else ""))
        except Exception as e:
            w("%s<节点读取异常: %s>" % ("  " * depth, e))
            continue
        walk(ch, depth + 1, budget)


def main():
    uia.SetGlobalSearchTimeout(1.0)
    win = find_top()
    if win is None:
        w("未找到抖音顶层窗口")
        return
    hwnd = win.NativeWindowHandle
    w("顶层窗口 hwnd=%s cls=%r name=%r" % (hwnd, win.ClassName, win.Name))

    fg = user32.GetForegroundWindow()
    w("抖音是否前台: %s" % (fg == hwnd))

    # 唤醒: 请求 UIA 对象 (与 QQ 的 wake_a11y 相同策略)
    for i in range(3):
        try:
            user32.SendMessageW(hwnd, WM_GETOBJECT, 0, ctypes.c_void_p(OBJID_CLIENT))
        except Exception:
            pass
        if fg == hwnd:
            try:
                user32.SetForegroundWindow(hwnd)
            except Exception:
                pass
        time.sleep(1.0)
        # 每次唤醒后试读一遍树的大小
        budget = [6000]
        lines_before = len(out.getvalue().splitlines())
        w("--- 第 %d 次唤醒后遍历 ---" % (i + 1))
        walk(win, 1, budget)
        got = len(out.getvalue().splitlines()) - lines_before
        w("(本次 %d 行, 剩余 budget %d)" % (got, budget[0]))
        if got > 15:
            break


if __name__ == "__main__":
    try:
        main()
    finally:
        with open("probe_douyin_deep.out.txt", "w", encoding="utf-8") as f:
            f.write(out.getvalue())
        print("\n[已写入 probe_douyin_deep.out.txt]")
