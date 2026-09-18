# -*- coding: utf-8 -*-
"""复现中文输入法误触发场景并验证修复:

  1. 输入框被放入 "mj"(等价 IME 字母上屏后的状态), 随后按下 Enter(等价上屏回车)
     -> 输入框仍是 "mj", 消息并未发送 -> 不应触发   [本次修复的 bug]
  2. 随后输入框被清空(等价真正发送成功)
     -> 命中候选 + 清空 + 发送键在时间窗内 -> 应触发一次
"""
import logging
import sys
import time

sys.path.insert(0, "..")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

import uiautomation as uia

from mjegg.config import DEFAULTS
from mjegg.watcher import Detector

cfg = dict(DEFAULTS)
cfg["ignore_injected_keys"] = False
cfg["poll_interval_ms"] = 50

INPUT_CLASS = cfg["input_control_class"]
triggered = []


def main():
    det = Detector(cfg, on_trigger=lambda: (triggered.append(time.time()), print(">>> TRIGGERED!")))
    det.start()
    time.sleep(1.0)

    top = None
    for w in uia.GetRootControl().GetChildren():
        if w.ClassName in ("DtMainFrameView", "DingChatWnd"):
            top = w
            break
    assert top is not None, "钉钉窗口未找到"
    edit = top.EditControl(ClassName=INPUT_CLASS)
    assert edit.Exists(1, 0.2), "输入框未找到"

    edit.SetFocus()
    time.sleep(0.4)
    uia.SendKeys("{Ctrl}a{Delete}", waitTime=0.1)  # 清空残留
    time.sleep(0.3)

    # --- 场景1: 模拟 IME 字母上屏后的状态(框里有 mj, 未发送) + 上屏回车 ---
    uia.SendKeys("mj", waitTime=0.1, interval=0.06)
    time.sleep(0.4)  # 让轮询登记"命中候选"
    from mjegg.foreground import get_foreground_info
    name, hwnd, _ = get_foreground_info()
    assert name and name.lower() == "dingtalk.exe", f"钉钉不在前台: {name}"
    v = edit.GetValuePattern().Value
    print("输入框状态(应含mj):", repr(v))
    assert v == "mj"

    det._last_send_ts = time.time()          # 模拟上屏回车(发送键事件)
    det._on_send_key(time.time(), ["M", "J"])
    time.sleep(0.8)
    assert not triggered, f"IME 上屏回车误触发了! {len(triggered)} 次"
    print("场景1(IME上屏回车,未发送): 未触发 OK  <- 本次修复的bug")

    # --- 场景2: 真正发送(输入框被清空) ---
    uia.SendKeys("{Ctrl}a{Delete}", waitTime=0.1)  # 等价发送成功后钉钉清空输入框
    time.sleep(0.6)
    assert len(triggered) == 1, f"清空后应触发恰好1次, 实际 {len(triggered)}"
    print("场景2(真正发送清空): 触发1次 OK")

    # 恢复
    edit.SetFocus()
    time.sleep(0.2)
    uia.SendKeys("{Ctrl}a{Delete}", waitTime=0.1)
    time.sleep(0.2)

    det.stop()
    print("\nIME 场景测试: 全部通过")


if __name__ == "__main__":
    main()
