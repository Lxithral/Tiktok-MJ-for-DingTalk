# -*- coding: utf-8 -*-
"""全树转储: 打印指定进程顶层窗口的 UIA 树(控件类型+类名+名称+矩形).

用法: python probe_tree.py Weixin.exe [最大行数]
"""
import sys
import io

import uiautomation as uia

out = io.StringIO()
MAX_DEPTH = 20


def w(s=""):
    print(s)
    out.write(s + "\n")


stats = {"nodes": 0, "edits": []}


def walk(node, depth, lines, limit):
    if depth > MAX_DEPTH or len(lines) >= limit:
        return
    try:
        children = node.GetChildren()
    except Exception:
        return
    for ch in children:
        if len(lines) >= limit:
            return
        stats["nodes"] += 1
        try:
            ct = ch.ControlTypeName.replace("Control", "")
            cls = ch.ClassName or ""
            name = (ch.Name or "").replace("\n", "\\n")[:40]
            r = ch.BoundingRectangle
            rect = "(%d,%d,%d,%d)" % (r.left, r.top, r.right, r.bottom)
        except Exception:
            continue
        mark = ""
        if "Edit" in ct or "Document" in ct or "Edit" in cls:
            mark = "  <<< EDIT"
            stats["edits"].append((depth, ct, cls, name, rect))
        lines.append("%s%s cls=%r name=%r %s%s" % (
            "  " * depth, ct, cls, name, rect, mark))
        walk(ch, depth + 1, lines, limit)


def main():
    proc = sys.argv[1]
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 400
    uia.SetGlobalSearchTimeout(0.5)
    root = uia.GetRootControl()
    import ctypes
    import ctypes.wintypes as wt
    wins = []
    for win in root.GetChildren():
        try:
            pid = win.ProcessId
            h = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)
            if not h:
                continue
            buf = ctypes.create_unicode_buffer(1024)
            size = wt.DWORD(1024)
            nm = ""
            if ctypes.windll.kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
                nm = buf.value.split("\\")[-1]
            ctypes.windll.kernel32.CloseHandle(h)
            if nm.lower() == proc.lower():
                wins.append(win)
        except Exception:
            continue
    w("进程 %s: 找到 %d 个顶层窗口" % (proc, len(wins)))
    for i, win in enumerate(wins):
        w("")
        w("### 顶层窗口[%d] cls=%r name=%r" % (i, win.ClassName, win.Name))
        lines = []
        walk(win, 1, lines, limit)
        for ln in lines:
            w(ln)
    w("")
    w("节点总数=%d, 可编辑候选=%d" % (stats["nodes"], len(stats["edits"])))
    for e in stats["edits"]:
        w("  EDIT depth=%d type=%s cls=%r name=%r rect=%s" % e)
    with open("probe_tree.out.txt", "w", encoding="utf-8") as f:
        f.write(out.getvalue())


if __name__ == "__main__":
    main()
