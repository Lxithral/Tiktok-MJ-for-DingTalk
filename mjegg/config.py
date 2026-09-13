# -*- coding: utf-8 -*-
"""配置加载: config.json 覆盖默认值. 文件位于 exe 旁边(打包后)或项目根目录(源码)."""
import json
import os

from .paths import app_dir

DEFAULTS = {
    "enabled": True,                      # 启动时是否启用彩蛋
    "process_names": ["DingTalk.exe"],    # 前台进程名(忽略大小写)
    "input_control_class": "im_chat::InputRichTextEdit",  # 聊天输入框 UIA 类名
    "poll_interval_ms": 60,               # 输入框轮询间隔
    "send_key_window_s": 0.8,             # 发送键(Enter/Alt+S)有效时间窗
    "match_window_s": 1.2,                # 输入框命中触发词后的有效时间窗
    "cooldown_s": 4.0,                    # 两次播放之间的冷却
    "overlay_height_ratio": 0.85,         # 动画高度占屏幕高度比例
    "volume": 1.0,                        # 音效音量 0.0~1.0
    "watchdog_s": 30,                     # 播放僵尸会话强制回收
    "ignore_injected_keys": True,         # 忽略 SendInput 注入的按键(自动化工具不会误触发)
    "fallback_vk_buffer": True,           # 输入框不可读时启用键盘 VK 缓冲兜底
}


def load_config():
    cfg = dict(DEFAULTS)
    path = os.path.join(app_dir(), "config.json")
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                user = json.load(f)
            for k in DEFAULTS:
                if k in user:
                    cfg[k] = user[k]
        except Exception as e:
            print(f"[config] 读取 config.json 失败, 使用默认配置: {e}", file=sys.stderr)
    return cfg
