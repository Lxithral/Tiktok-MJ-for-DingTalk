# -*- coding: utf-8 -*-
"""POC2: 定位钉钉聊天输入框 -> 聚焦 -> 安全键入 mj(不回车) -> 读 Value -> 清空恢复.
全程不发送任何消息."""
import time
import uiautomation as uia

INPUT_CLASS = "im_chat::InputRichTextEdit"


def find_ding_windows():
    out = []
    for w in uia.GetRootControl().GetChildren():
        try:
            if w.ClassName in ("DtMainFrameView", "DingChatWnd"):
                out.append(w)
        except Exception:
            pass
    return out


def find_input(win):
    try:
        edits = win.GetFirstChildControl().FindAll(
            lambda c, d: c.ClassName == INPUT_CLASS, maxDepth=30, timeout=0.5)
        if edits:
            return edits[0]
    except Exception:
        pass
    # 兜底: 全树搜索 EditControl
    e = win.EditControl(ClassName=INPUT_CLASS, timeout=1)
    return e if e.Exists(0.5, 1) else None


def main():
    wins = find_ding_windows()
    print("钉钉顶层窗口:", [(w.ClassName, w.Name) for w in wins])
    if not wins:
        print("钉钉窗口未找到")
        return
    target = None
    edit = None
    for w in wins:
        e = find_input(w)
        if e is not None and e.Exists(0.5, 1):
            target, edit = w, e
            break
    if edit is None:
        print("未找到输入框(可能没有打开的会话)")
        return
    print("输入框:", edit.ControlTypeName, edit.ClassName)
    print("AutomationId:", edit.AutomationId)

    vp = edit.GetValuePattern()
    print("初始 Value =", repr(vp.Value))

    # 聚焦输入框
    try:
        edit.SetFocus()
    except Exception as ex:
        print("SetFocus 失败:", ex, " -> 尝试 Click")
        edit.Click(simulateMove=False)
    time.sleep(0.3)

    def read(tag):
        try:
            print(f"  [{tag}] Value = {vp.Value!r}")
            return vp.Value or ""
        except Exception as ex:
            print(f"  [{tag}] 读取失败: {ex}")
            return None

    # 键入 mj
    uia.SendKeys("mj", waitTime=0.1, interval=0.05)
    time.sleep(0.15)
    v1 = read("输入mj后")
    time.sleep(0.4)
    v2 = read("400ms后再读")  # 验证实时性

    # 追加 MJ -> mjmJM?  先清掉再试大写
    uia.SendKeys("{Ctrl}a", waitTime=0.05)
    uia.SendKeys("{Delete}", waitTime=0.05)
    time.sleep(0.1)
    read("清空后")

    uia.SendKeys("MjmJ", waitTime=0.1, interval=0.05)
    time.sleep(0.15)
    v3 = read("输入MjmJ后")

    # 恢复: 清空输入框
    uia.SendKeys("{Ctrl}a", waitTime=0.05)
    uia.SendKeys("{Delete}", waitTime=0.05)
    time.sleep(0.1)
    v4 = read("恢复后")

    ok = (v1 == "mj" and v2 == "mj" and v3 == "MjmJ" and v4 == "")
    print("\n结论:", "PASS - 输入框 Value 实时可读" if ok else "需人工检查上方输出")


if __name__ == "__main__":
    main()
