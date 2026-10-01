# -*- coding: utf-8 -*-
"""探测抖音 PC 版 (douyin.exe) 的 UIA 树, 找出聊天输入框的类名与可用 Pattern.

用法: python probe_douyin.py
结果同时打印并写入 probe_douyin.out.txt (UTF-8).
"""
import ctypes
import ctypes.wintypes as wt
import io
import sys

import uiautomation as uia

PROC = "douyin.exe"
MAX_DEPTH = 16
out = io.StringIO()


def w(s=""):
    print(s)
    out.write(s + "\n")


def proc_name(pid):
    h = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)
    nm = ""
    if h:
        buf = ctypes.create_unicode_buffer(1024)
        size = wt.DWORD(1024)
        if ctypes.windll.kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
            nm = buf.value.split("\\")[-1]
        ctypes.windll.kernel32.CloseHandle(h)
    return nm


def dump(node, depth, lines, budget):
    if depth > MAX_DEPTH or budget[0] <= 0:
        return
    try:
        children = node.GetChildren()
    except Exception:
        return
    for ch in children:
        budget[0] -= 1
        if budget[0] <= 0:
            return
        try:
            ct = ch.ControlTypeName
            cls = ch.ClassName or ""
            name = (ch.Name or "").replace("\n", " ")[:60]
            aid = ch.AutomationId or ""
        except Exception:
            continue
        is_editish = (
            "Edit" in ct or "Document" in ct
            or "Edit" in cls or "RichEdit" in cls or "RICHEDIT" in cls
            or "text" in cls.lower() or "input" in cls.lower()
        )
        if is_editish:
            pats = []
            for pname in ("ValuePattern", "TextPattern", "LegacyIAccessiblePattern"):
                try:
                    p = getattr(ch, "Get" + pname)()
                    if p:
                        pats.append(pname)
                except Exception:
                    pass
            val = ""
            try:
                vp = ch.GetValuePattern()
                if vp:
                    val = (vp.Value or "")[:40]
            except Exception:
                pass
            lines.append(
                "  %s[%s] cls=%r name=%r aid=%r pats=%s value=%r"
                % ("  " * depth, ct, cls, name, aid, ",".join(pats), val)
            )
        dump(ch, depth + 1, lines, budget)


def main():
    uia.SetGlobalSearchTimeout(1.0)
    root = uia.GetRootControl()
    wins = []
    for win in root.GetChildren():
        try:
            if proc_name(win.ProcessId).lower() == PROC:
                wins.append(win)
        except Exception:
            continue
    if not wins:
        w("(没有找到 %s 的顶层窗口 — 可能未运行)" % PROC)
        return
    w("共 %d 个顶层窗口" % len(wins))
    for i, win in enumerate(wins):
        try:
            w("=" * 78)
            w("窗口[%d] cls=%r name=%r rect=%s" % (
                i, win.ClassName, (win.Name or "")[:50], win.BoundingRectangle))
        except Exception:
            w("窗口[%d] (读取失败)" % i)
            continue
        lines = []
        dump(win, 1, lines, [6000])
        if lines:
            for ln in lines[:80]:
                w(ln)
        else:
            w("    (无可编辑控件 — UIA 树可能是空的/未暴露)")


if __name__ == "__main__":
    try:
        main()
    finally:
        with open("probe_douyin.out.txt", "w", encoding="utf-8") as f:
            f.write(out.getvalue())
        print("\n[已写入 probe_douyin.out.txt]")
