# -*- coding: utf-8 -*-
"""探测各 IM 输入框的读取方式: 支持哪些 Pattern, 文本怎么读.

用法: python probe_inputs.py
"""
import ctypes
import ctypes.wintypes as wt
import io
import time

import uiautomation as uia

out = io.StringIO()

TARGETS = [
    ("Weixin.exe", "mmui::ChatInputField"),
    ("QQ.exe", "ExEditor-qq-msg-editor"),
]

PATTERNS = [
    ("ValuePattern", "GetValuePattern"),
    ("TextPattern", "GetTextPattern"),
    ("LegacyIAccessiblePattern", "GetLegacyIAccessiblePattern"),
    ("TextEditPattern", "GetTextEditPattern"),
]


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


def top_windows(proc):
    res = []
    EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        pid = wt.DWORD()
        ctypes.windll.user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if proc_name_of(pid.value).lower() == proc.lower():
            cls = ctypes.create_unicode_buffer(256)
            ctypes.windll.user32.GetClassNameW(hwnd, cls, 256)
            rc = wt.RECT()
            ctypes.windll.user32.GetWindowRect(hwnd, ctypes.byref(rc))
            if ctypes.windll.user32.IsWindowVisible(hwnd) and rc.right > rc.left:
                res.append((hwnd, cls.value, (rc.left, rc.top, rc.right, rc.bottom)))
        return True
    ctypes.windll.user32.EnumWindows(EnumProc(cb), 0)
    return res


def find_by_class(node, needle, depth=0, hits=None, max_depth=25):
    if hits is None:
        hits = []
    if depth > max_depth or len(hits) >= 6:
        return hits
    try:
        children = node.GetChildren()
    except Exception:
        return hits
    for ch in children:
        try:
            cls = ch.ClassName or ""
            if needle in cls:
                hits.append(ch)
        except Exception:
            pass
        find_by_class(ch, needle, depth + 1, hits, max_depth)
    return hits


def describe(node):
    try:
        w("    控件: type=%s cls=%r name=%r" % (
            node.ControlTypeName, node.ClassName, (node.Name or "")[:60]))
        r = node.BoundingRectangle
        w("    矩形: (%d,%d,%d,%d)" % (r.left, r.top, r.right, r.bottom))
    except Exception as e:
        w("    读取基本属性失败: %s" % e)
        return
    for label, getter in PATTERNS:
        try:
            p = getattr(node, getter)()
            if not p:
                w("    %-26s 不支持" % label)
                continue
            extra = ""
            try:
                if label == "ValuePattern":
                    extra = " value=%r readonly=%s" % (p.Value, p.IsReadOnly)
                elif label == "TextPattern":
                    extra = " text=%r" % (p.DocumentRange.GetText(200),)
                elif label == "LegacyIAccessiblePattern":
                    extra = " value=%r" % (p.Value,)
            except Exception as e:
                extra = " (读取值失败: %s)" % e
            w("    %-26s 支持%s" % (label, extra))
        except Exception as e:
            w("    %-26s 异常: %s" % (label, e))
    # 子节点文本
    try:
        kids = node.GetChildren()
        w("    子节点数=%d" % len(kids))
        for k in kids[:8]:
            try:
                w("      - type=%s cls=%r name=%r" % (
                    k.ControlTypeName, k.ClassName, (k.Name or "")[:50]))
            except Exception:
                pass
    except Exception as e:
        w("    子节点读取失败: %s" % e)


def main():
    uia.SetGlobalSearchTimeout(1.0)
    for proc, needle in TARGETS:
        w("=" * 78)
        w("### %s  (输入框类名包含 %r)" % (proc, needle))
        wins = top_windows(proc)
        if not wins:
            w("  进程未运行")
            continue
        found = False
        for hwnd, cls, rect in wins:
            try:
                win = uia.ControlFromHandle(hwnd)
            except Exception:
                continue
            hits = find_by_class(win, needle)
            if hits:
                found = True
                w("  窗口 hwnd=%s cls=%r rect=%s -> 命中 %d 个" % (hwnd, cls, rect, len(hits)))
                for h in hits:
                    describe(h)
        if not found:
            w("  未找到该输入框(可能 a11y 未开启 / 不在聊天界面)")
    with open("probe_inputs.out.txt", "w", encoding="utf-8") as f:
        f.write(out.getvalue())


if __name__ == "__main__":
    main()
