# -*- coding: utf-8 -*-
"""检测 worker: 键盘钩子事件 + UIA 输入框轮询, 命中触发词时回调.

触发判定 = "输入框命中过触发词" + "输入框被清空" + "发送键刚按下" 三者同时成立:
  - 发送键(Enter/Alt+S)按下瞬间读输入框: 命中只登记候选, 不直接触发
    (中文输入法下 Enter 可能只是字母上屏, 消息并未发送);
  - 轮询确认输入框被清空且发送键在时间窗内 -> 确认发送完成 -> 触发;
  - 该判据同时覆盖回车发送、Alt+S、鼠标点击发送按钮, 以及钉钉先清框后我们才读到的竞态.
兜底 B: 输入框 UIA 不可读时, 退化为检查键盘 VK 缓冲尾部(无法观测清空, 纯启发式).
"""
import logging
import threading
import time

import uiautomation as uia

from .foreground import get_foreground_info
from .keyhook import KeyHookThread
from .matcher import matches_trigger, matches_vk_buffer

log = logging.getLogger("mjegg.watcher")


class Detector(threading.Thread):
    def __init__(self, cfg, on_trigger):
        super().__init__(daemon=True, name="MJEggDetector")
        self.cfg = cfg
        self.on_trigger = on_trigger  # 主线程侧回调(Qt 信号桥)
        self.hook = None
        self._stop = threading.Event()
        self._last_trigger_ts = 0.0
        # UIA 缓存
        self._edit = None
        self._value_pattern = None
        self._edit_top_hwnd = 0
        self._find_fail_streak = 0
        self._use_fallback = False
        # 状态
        self._last_match_ts = 0.0
        self._last_send_ts = 0.0

    # ---------- 生命周期 ----------
    def run(self):
        try:
            import comtypes
            comtypes.CoInitialize()  # 本线程使用 UIA 必须初始化 COM
        except Exception:
            pass
        self.hook = KeyHookThread(ignore_injected=self.cfg["ignore_injected_keys"])
        self.hook.start()
        log.info("检测线程启动 poll=%dms", self.cfg["poll_interval_ms"])
        poll = self.cfg["poll_interval_ms"] / 1000.0
        while not self._stop.is_set():
            try:
                self._drain_send_events()
                self._poll_tick()
            except Exception as e:
                log.exception("tick 异常: %s", e)
            self._stop.wait(poll)
        log.info("检测线程退出")

    def stop(self):
        self._stop.set()

    # ---------- 事件 ----------
    def _drain_send_events(self):
        while True:
            try:
                ts, snapshot = self.hook.send_events.get_nowait()
            except Exception:
                break
            self._last_send_ts = ts
            self._on_send_key(ts, snapshot)

    def _on_send_key(self, ts, vk_snapshot):
        if self._in_cooldown():
            return
        name, hwnd, _ = get_foreground_info()
        if not name or name.lower() not in {n.lower() for n in self.cfg["process_names"]}:
            return
        # 主路径 A: Enter 瞬间读输入框. 注意: 命中也不直接触发——
        # 中文输入法下按 Enter 只是把字母"上屏"进输入框(消息并未发送),
        # 直接触发会误报. 这里只登记候选, 由轮询在"输入框被清空"时确认发送后触发.
        v = self._read_input(hwnd)
        if v is not None:
            if v and matches_trigger(v):
                self._last_match_ts = time.time()
            return
        # 兜底 B: UIA 不可读时按 VK 缓冲启发式直接触发(无法观测清空, 维持旧行为)
        if self.cfg["fallback_vk_buffer"] and (self._use_fallback or v is None):
            if matches_vk_buffer(vk_snapshot):
                self._fire("vk缓冲 %s" % "".join(vk_snapshot[-8:]))

    # ---------- 轮询 ----------
    def _poll_tick(self):
        name, hwnd, _ = get_foreground_info()
        if not name or name.lower() not in {n.lower() for n in self.cfg["process_names"]}:
            self._invalidate_edit()
            return
        if self._in_cooldown():
            return
        v = self._read_input(hwnd, allow_refind=True)
        if v is None:
            return
        now = time.time()
        if v:
            if matches_trigger(v):
                self._last_match_ts = now
        else:
            # 命中过触发词 + 变空 + 发送键刚按下 => 发送完成
            if (
                now - self._last_match_ts <= self.cfg["match_window_s"]
                and now - self._last_send_ts <= self.cfg["send_key_window_s"]
            ):
                self._fire("输入框清空+发送键")

    # ---------- UIA ----------
    def _read_input(self, fg_hwnd, allow_refind=False):
        try:
            if self._value_pattern is None or self._edit is None or (
                allow_refind and self._edit_top_hwnd != fg_hwnd
            ):
                if not allow_refind and self._edit is None:
                    return None
                if not self._ensure_edit(fg_hwnd):
                    return None
            val = self._value_pattern.Value
            self._find_fail_streak = 0
            self._use_fallback = False
            return val or ""
        except Exception:
            self._invalidate_edit()
            return None

    def _ensure_edit(self, fg_hwnd):
        if not fg_hwnd:
            return False
        try:
            top = uia.ControlFromHandle(fg_hwnd)
            edit = top.EditControl(ClassName=self.cfg["input_control_class"])
            if not edit.Exists(0.2, 0.05):
                self._find_fail_streak += 1
                if self._find_fail_streak >= 5:
                    self._use_fallback = True
                return False
            self._edit = edit
            self._value_pattern = edit.GetValuePattern()
            self._edit_top_hwnd = fg_hwnd
            self._find_fail_streak = 0
            log.info("输入框已定位 hwnd=%s", fg_hwnd)
            return True
        except Exception as e:
            self._find_fail_streak += 1
            if self._find_fail_streak >= 5:
                self._use_fallback = True
            log.debug("定位输入框失败: %s", e)
            return False

    def _invalidate_edit(self):
        self._edit = None
        self._value_pattern = None
        self._edit_top_hwnd = 0

    # ---------- 触发 ----------
    def _in_cooldown(self):
        return time.time() - self._last_trigger_ts < self.cfg["cooldown_s"]

    def _fire(self, reason):
        self._last_trigger_ts = time.time()
        # 触发后重置状态, 防止残留状态造成连环触发
        self._last_match_ts = 0.0
        self._last_send_ts = 0.0
        log.info("触发彩蛋: %s", reason)
        try:
            self.on_trigger()
        except Exception:
            log.exception("on_trigger 回调异常")
