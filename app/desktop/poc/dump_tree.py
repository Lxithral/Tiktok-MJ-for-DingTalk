# -*- coding: utf-8 -*-
"""POC: 检查钉钉 PC 窗口的 UIA 控件树, 重点找聊天输入框(Edit)及其 Value 可读性."""
import sys
import uiautomation as uia


def short(c):
    name = (c.Name or "").replace("\n", "\\n")
    if len(name) > 24:
        name = name[:24] + ".."
    return f"{c.ControlTypeName} name='{name}' class='{c.ClassName}' id='{c.AutomationId}'"


def walk(ctrl, depth, max_depth, hits):
    if depth > max_depth:
        return
    try:
        children = ctrl.GetChildren()
    except Exception as e:
        print("  " * depth + f"<err {e}>")
        return
    for ch in children:
        line = "  " * depth + short(ch)
        has_value = False
        val_repr = ""
        if ch.ControlTypeName in ("EditControl", "DocumentControl", "TextEditControl"):
            try:
                vp = ch.GetValuePattern()
                v = vp.Value
                has_value = True
                val_repr = (v or "").replace("\n", "\\n")[:40]
            except Exception as e:
                val_repr = f"<no ValuePattern: {type(e).__name__}>"
            hits.append((depth, line, val_repr))
        print(line + (f"  VALUE={val_repr!r}" if has_value or val_repr else ""))
        walk(ch, depth + 1, max_depth, hits)


def main():
    max_depth = int(sys.argv[1]) if len(sys.argv) > 1 else 14
    wins = uia.GetRootControl().GetChildren()
    ding = None
    for w in wins:
        try:
            if w.ClassName == "DtMainFrameView":
                ding = w
                print(f"TOP: {short(w)}  pid={w.ProcessId}")
                break
        except Exception:
            pass
    if ding is None:
        print("未找到钉钉顶层窗口(DtMainFrameView); 顶层窗口列表:")
        for w in wins:
            try:
                print(f"  {short(w)} pid={w.ProcessId}")
            except Exception:
                pass
        return
    hits = []
    walk(ding, 0, max_depth, hits)
    print("\n===== Edit/Document 控件汇总 =====")
    for d, line, v in hits:
        print(f"[depth {d}] {line}  VALUE={v!r}")


if __name__ == "__main__":
    main()
