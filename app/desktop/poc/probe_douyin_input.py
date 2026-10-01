# -*- coding: utf-8 -*-
"""定位抖音聊天输入框: 找占位符 '发送消息' 节点, 打印祖先链与兄弟结构,
确认可用作匹配的稳定标识 (AutomationId / 结构特征) 与读文本路径.

用法: python probe_douyin_input.py
结果打印并写入 probe_douyin_input.out.txt (UTF-8).
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
            if win.ClassName != "Chrome_WidgetWin_1":
                continue
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


def desc(node):
    try:
        return "[%s] cls=%r name=%r aid=%r" % (
            node.ControlTypeName, node.ClassName or "",
            (node.Name or "").replace("\n", " ")[:40], node.AutomationId or "")
    except Exception as e:
        return "<%s>" % e


def walk_collect(node, depth, hits, budget):
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
            nm = ch.Name or ""
            if nm == "发送消息" or nm == "\u200b":
                hits.append(ch)
        except Exception:
            pass
        walk_collect(ch, depth + 1, hits, budget)


def main():
    uia.SetGlobalSearchTimeout(1.0)
    win = find_top()
    if win is None:
        w("未找到抖音顶层窗口")
        return
    w("顶层窗口 hwnd=%s" % win.NativeWindowHandle)
    hwnd = win.NativeWindowHandle

    # 唤醒 + 确认前台 (与 QQ wake_a11y 同策略)
    for i in range(3):
        fg = user32.GetForegroundWindow()
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

    hits = []
    walk_collect(win, 1, hits, [8000])
    w("找到 %d 个候选节点 (发送消息 / 零宽字符)" % len(hits))

    for i, node in enumerate(hits):
        w("=" * 70)
        w("候选[%d] 自身: %s" % (i, desc(node)))
        # 祖先链
        cur = node
        depth = 0
        try:
            while depth < 25:
                cur = cur.GetParentControl()
                if cur is None:
                    break
                w("  ↑父%d: %s" % (depth, desc(cur)))
                depth += 1
        except Exception as e:
            w("  祖先遍历异常: %s" % e)
        # 兄弟
        try:
            p = node.GetParentControl()
            if p is not None:
                w("  -- 兄弟节点 --")
                for j, sib in enumerate(p.GetChildren()[:10]):
                    w("  sib%d: %s" % (j, desc(sib)))
        except Exception as e:
            w("  兄弟遍历异常: %s" % e)


if __name__ == "__main__":
    try:
        main()
    finally:
        with open("probe_douyin_input.out.txt", "w", encoding="utf-8") as f:
            f.write(out.getvalue())
        print("\n[已写入 probe_douyin_input.out.txt]")
