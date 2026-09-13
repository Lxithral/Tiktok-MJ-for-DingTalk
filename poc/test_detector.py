# -*- coding: utf-8 -*-
"""检测端完整链路测试(不发送真实消息).

说明: 脚本注入的按键在低级钩子里 vkCode=0xE7(无真实VK), 但带扫描码可还原,
     真实键盘按键不受影响; 生产默认 ignore_injected_keys=True 只认真实键盘.

  1. 启动 Detector(真实键盘钩子线程)
  2. 聚焦钉钉聊天输入框, 注入键入 "mj"(仅键入, 不回车, 不发送)
  3. 验证钩子收到按键事件且 vk_buffer 还原出 M,J  -> 钩子存活
  4. 手动派发"发送键"事件(等价 Enter 瞬间)        -> 主路径A: 读输入框命中 -> 触发
  5. 立刻再派发一次                                -> 冷却期内不得二次触发
  6. 清空输入框恢复
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
cfg["ignore_injected_keys"] = False  # 测试需要: 让注入键进入钩子
cfg["poll_interval_ms"] = 50

INPUT_CLASS = cfg["input_control_class"]
triggered = []


def main():
    det = Detector(cfg, on_trigger=lambda: (triggered.append(time.time()), print(">>> TRIGGERED!")))
    det.start()
    time.sleep(1.0)

    # 找钉钉窗口与输入框
    top = None
    for w in uia.GetRootControl().GetChildren():
        if w.ClassName in ("DtMainFrameView", "DingChatWnd"):
            top = w
            break
    assert top is not None, "钉钉窗口未找到"
    edit = top.EditControl(ClassName=INPUT_CLASS)
    assert edit.Exists(1, 0.2), "输入框未找到"

    edit.SetFocus()
    time.sleep(0.5)

    # 先清掉历史残留草稿(此前失败测试可能留下了未发送内容)
    uia.SendKeys("{Ctrl}a{Delete}", waitTime=0.1)
    time.sleep(0.3)
    pre = edit.GetValuePattern().Value
    print("键入前输入框:", repr(pre))

    # 注入键入 mj(不回车, 不会发送)
    uia.SendKeys("mj", waitTime=0.1, interval=0.06)
    time.sleep(0.4)
    cnt = det.hook.event_count
    buf = list(det.hook.vk_buffer)
    print("钩子事件数:", cnt, " vk_buffer:", buf)
    assert cnt >= 4, f"钩子未收到事件: {cnt}"
    assert buf == ["M", "J"], f"VK缓冲(扫描码还原)异常: {buf}"

    # 确认输入框内容
    v = edit.GetValuePattern().Value
    print("输入框内容:", repr(v))
    assert v == "mj", f"输入框内容异常: {v!r}"

    # 前台必须是钉钉
    from mjegg.foreground import get_foreground_info
    name, hwnd, _ = get_foreground_info()
    print("前台进程:", name)
    assert name and name.lower() == "dingtalk.exe", f"钉钉不在前台: {name}"

    # 手动派发发送键事件(等价 Enter 瞬间). 新语义: 命中只登记候选, 不直接触发
    det._on_send_key(time.time(), ["M", "J"])
    time.sleep(0.3)
    assert not triggered, "Enter命中不应直接触发(需等清空确认发送)!"
    print("Enter命中仅登记候选, 不直接触发 OK")

    # 模拟发送成功: 输入框被清空 -> 轮询确认后触发
    uia.SendKeys("{Ctrl}a{Delete}", waitTime=0.1)
    time.sleep(0.5)
    assert len(triggered) == 1, f"清空后应触发1次, 实际 {len(triggered)}"
    print("清空确认后触发1次 OK")

    # 冷却测试: 再次派发送键+清空, 应被冷却抑制
    n = len(triggered)
    uia.SendKeys("mj", waitTime=0.1, interval=0.05)
    time.sleep(0.2)
    det._on_send_key(time.time(), ["M", "J"])
    time.sleep(0.2)
    uia.SendKeys("{Ctrl}a{Delete}", waitTime=0.1)
    time.sleep(0.5)
    assert len(triggered) == n, "冷却期内二次触发了!"
    print("冷却抑制 OK")

    # 恢复: 清空输入框
    edit.SetFocus()
    time.sleep(0.2)
    uia.SendKeys("{Ctrl}a{Delete}", waitTime=0.1)
    time.sleep(0.3)
    v = edit.GetValuePattern().Value
    print("输入框已清空:", repr(v))

    det.stop()
    print("\n检测端链路测试: 全部通过")


if __name__ == "__main__":
    main()
