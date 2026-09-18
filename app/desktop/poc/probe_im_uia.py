# -*- coding: utf-8 -*-
"""探测钉钉/微信/QQ 的 UIA 树, 找出聊天输入框的类名与可用 Pattern.

用法: python probe_im_uia.py [进程名...]
不传参数则探测 DingTalk.exe / Weixin.exe / QQ.exe.
结果同时打印并写入 probe_im_uia.out.txt (UTF-8).
"""
import sys
import time
import io

import uiautomation as uia

TARGETS = ["DingTalk.exe", "Weixin.exe", "QQ.exe"]
MAX_DEPTH = 14
out = io.StringIO()


def w(s=""):
    print(s)
    out.write(s + "\n")


def dump(node, depth, lines, budget):
    """递归遍历, 收集可能是输入框的节点."""
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


def probe(proc_name):
    w("=" * 78)
    w("进程: %s" % proc_name)
    root = uia.GetRootControl()
    wins = []
    try:
        for win in root.GetChildren():
            try:
                pid = win.ProcessId
                p = uia.ProcessIdToProcessName(pid) if hasattr(uia, "ProcessIdToProcessName") else None
            except Exception:
                continue
            try:
                import ctypes
                import ctypes.wintypes as wt
                h = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)
                buf = ctypes.create_unicode_buffer(1024)
                size = wt.DWORD(1024)
                nm = ""
                if h:
                    if ctypes.windll.kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
                        nm = buf.value.split("\\")[-1]
                    ctypes.windll.kernel32.CloseHandle(h)
                if nm.lower() == proc_name.lower():
                    wins.append(win)
            except Exception:
                continue
    except Exception as e:
        w("  枚举顶层窗口失败: %s" % e)
        return
    if not wins:
        w("  (没有找到该进程的顶层窗口 — 可能未运行)")
        return
    for i, win in enumerate(wins):
        try:
            w("  窗口[%d] cls=%r name=%r rect=%s" % (
                i, win.ClassName, (win.Name or "")[:50], win.BoundingRectangle))
        except Exception:
            w("  窗口[%d] (读取失败)" % i)
            continue
        lines = []
        dump(win, 1, lines, [4000])
        if lines:
            for ln in lines[:60]:
                w(ln)
        else:
            w("    (无可编辑控件 — UIA 树可能是空的/未暴露)")


def main():
    targets = sys.argv[1:] or TARGETS
    uia.SetGlobalSearchTimeout(1.0)
    for t in targets:
        try:
            probe(t)
        except Exception as e:
            w("  探测 %s 异常: %s" % (t, e))
    with open("probe_im_uia.out.txt", "w", encoding="utf-8") as f:
        f.write(out.getvalue())
    print("\n[已写入 probe_im_uia.out.txt]")


if __name__ == "__main__":
    main()
