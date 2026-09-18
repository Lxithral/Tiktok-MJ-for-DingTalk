# -*- coding: utf-8 -*-
"""配置加载: config.json 覆盖默认值. 文件位于 exe 旁边(打包后)或项目根目录(源码)."""
import json
import os
import sys

from .im_targets import DEFAULT_TARGETS
from .paths import app_dir

DEFAULTS = {
    "enabled": True,                      # 启动时是否启用彩蛋
    # 支持的目标 IM: 进程名 + 聊天输入框 UIA 类名 (+可选 wake_a11y 唤醒无障碍树)
    "targets": [dict(t) for t in DEFAULT_TARGETS],
    "poll_interval_ms": 60,               # 输入框轮询间隔
    "send_key_window_s": 0.8,             # 发送键(Enter/Alt+S)有效时间窗
    "match_window_s": 1.2,                # 输入框命中触发词后的有效时间窗
    "cooldown_s": 4.0,                    # 两次播放之间的冷却
    "overlay_height_ratio": 0.85,         # 动画高度占屏幕高度比例
    "volume": 1.0,                        # 音效音量 0.0~1.0
    "watchdog_s": 30,                     # 播放僵尸会话强制回收
    # 注入按键策略: ignore / unsigned / auto / allow (见 keyhook.py 顶部说明)
    # auto = 屏幕键盘开着时全收, 否则只收无签名(dwExtraInfo==0)的注入
    "injected_key_policy": "auto",
    # 这些进程在运行时视为"用户在用屏幕键盘", auto 策略据此放宽
    "screen_keyboard_processes": [
        "osk.exe",            # Windows 屏幕键盘
        "TabTip.exe",         # 触摸键盘
        "TextInputHost.exe",  # Win11 输入法/触摸键盘宿主
        "WindowsInternal.ComposableShell.Experiences.TextInput.InputApp.exe",
    ],
    "fallback_vk_buffer": True,           # 输入框不可读时启用键盘 VK 缓冲兜底
    "ime_aware": True,                    # 回车遇输入法组合态时判为"上屏"而非"发送"
}

# 旧版布尔键 -> 新策略名的迁移映射
_LEGACY_INJECTED = {True: "ignore", False: "allow"}

_PROC_CLASS = {t["process"].lower(): t["input_class"] for t in DEFAULT_TARGETS}


def _migrate_legacy(user: dict) -> dict:
    """把旧版配置键迁移到新结构."""
    out = {}
    # process_names + input_control_class -> targets
    names = user.get("process_names")
    legacy_class = user.get("input_control_class")
    if names or legacy_class:
        targets = []
        for name in (names or []):
            cls = _PROC_CLASS.get(str(name).lower(), legacy_class)
            if cls:
                targets.append({"process": name, "input_class": cls})
        if not targets and legacy_class:
            targets = [{"process": "DingTalk.exe", "input_class": legacy_class}]
        out["targets"] = targets
    # ignore_injected_keys(bool) -> injected_key_policy
    if "ignore_injected_keys" in user:
        out["injected_key_policy"] = _LEGACY_INJECTED.get(
            bool(user["ignore_injected_keys"]), "auto")
    return out


def load_config():
    cfg = dict(DEFAULTS)
    path = os.path.join(app_dir(), "config.json")
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                user = json.load(f)
            # 旧键迁移(仅当新键缺失时才覆盖, 新键优先)
            for k, v in _migrate_legacy(user).items():
                if k not in user:
                    user[k] = v
            for k in DEFAULTS:
                if k in user:
                    cfg[k] = user[k]
            # targets 里缺字段的项用内置默认补齐
            merged = []
            for spec in cfg["targets"]:
                if not isinstance(spec, dict):
                    continue
                item = {"process": spec.get("process", ""),
                        "input_class": spec.get("input_class", "")}
                if spec.get("match"):
                    item["match"] = spec["match"]
                if spec.get("wake_a11y"):
                    item["wake_a11y"] = True
                if item["process"] and item["input_class"]:
                    merged.append(item)
            if merged:
                cfg["targets"] = merged
        except Exception as e:
            print(f"[config] 读取 config.json 失败, 使用默认配置: {e}", file=sys.stderr)
    if not cfg["targets"]:
        cfg["targets"] = [dict(t) for t in DEFAULT_TARGETS]
    return cfg
