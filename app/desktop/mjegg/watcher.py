# -*- coding: utf-8 -*-
"""检测 worker: 键盘钩子事件 + UIA 输入框轮询, 命中触发词时回调.

支持钉钉 / 微信 / QQ 三个客户端(见 im_targets.py), 逻辑与判据与原项目一致:

触发判定 = "输入框命中过触发词" + "输入框被清空" + "发送键刚按下" 三者同时成立:
  - 发送键(Enter/Alt+S)按下瞬间读输入框: 命中只登记候选, 不直接触发
    (中文输入法下 Enter 可能只是字母上屏, 消息并未发送);
  - 轮询确认输入框被清空且发送键在时间窗内 -> 确认发送完成 -> 触发;
  - 该判据同时覆盖回车发送、Alt+S、鼠标点击发送按钮, 以及客户端先清框后我们才读到的竞态.

兜底 B: 输入框 UIA 不可读时(例如 Chromium 壳的无障碍树没起来), 退化为
"键盘缓冲整串命中", 组合态回车已在 keyhook 里被排除, 所以不会误报输入法上屏.
"""
import logging
import threading
import time

from .foreground import get_foreground_info, processes_present
from .im_targets import build_targets, find_target
from .keyhook import KeyHookThread
from .matcher import matches_trigger, matches_vk_buffer

log = logging.getLogger("mjegg.watcher")

# 屏幕键盘存在性探测的最小间隔(秒): auto 注入策略据此放宽
SCREEN_KB_POLL_S = 2.0


class Detector(threading.Thread):
    def __init__(self, cfg, on_trigger):
        super().__init__(daemon=True, name="MJEggDetector")
        self.cfg = cfg
        self.on_trigger = on_trigger  # 主线程侧回调(Qt 信号桥)
        self.targets = build_targets(cfg["targets"])
        self.hook = None
        self._stop = threading.Event()
        self._last_trigger_ts = 0.0
        # 当前目标
        self._target = None
        self._find_fail_streak = 0
        self._use_fallback = False
        # 状态
        self._last_match_ts = 0.0
        self._last_send_ts = 0.0
        self._last_screen_kb_check = 0.0

    # ---------- 生命周期 ----------
    def run(self):
        try:
            import comtypes
            comtypes.CoInitialize()  # 本线程使用 UIA 必须初始化 COM
        except Exception:
            pass
        self.hook = KeyHookThread(
            injected_policy=self.cfg.get("injected_key_policy", "auto"),
            ime_aware=self.cfg.get("ime_aware", True))
        self.hook.start()
        log.info("检测线程启动 poll=%dms 注入按键策略=%s 目标=%s",
                 self.cfg["poll_interval_ms"],
                 self.cfg.get("injected_key_policy", "auto"),
                 ", ".join(t.process for t in self.targets) or "(无)")
        poll = self.cfg["poll_interval_ms"] / 1000.0
        while not self._stop.is_set():
            try:
                self._drain_send_events()
                self._refresh_screen_keyboard()
                self._poll_tick()
            except Exception as e:
                log.exception("tick 异常: %s", e)
            self._stop.wait(poll)
        log.info("检测线程退出")

    def _refresh_screen_keyboard(self):
        """定期探测屏幕键盘是否开着(供 keyhook 的 auto 注入策略使用)."""
        now = time.time()
        if now - self._last_screen_kb_check < SCREEN_KB_POLL_S:
            return
        self._last_screen_kb_check = now
        names = self.cfg.get("screen_keyboard_processes") or []
        try:
            active = processes_present(names)
        except Exception:
            return
        if active != self.hook.screen_keyboard_active:
            self.hook.screen_keyboard_active = active
            log.info("屏幕键盘%s, 注入按键按 %s 处理", "已打开" if active else "已关闭",
                     "全部接受" if active else "仅接受无签名")


    def stop(self):
        self._stop.set()

    # ---------- 目标切换 ----------
    def _current_target(self, process_name):
        t = find_target(process_name, self.targets)
        if t is not self._target:
            if self._target is not None:
                self._target.invalidate()
            self._target = t
            self._find_fail_streak = 0
            self._use_fallback = False
            if t is not None:
                log.info("目标切换 -> %s", t.process)
        return t

    # ---------- 事件 ----------
    def _drain_send_events(self):
        while True:
            try:
                ts, snapshot = self.hook.send_events.get_nowait()
            except Exception:
                break
            try:
                self._on_send_key(ts, snapshot)
            except Exception as e:
                log.exception("处理发送键事件异常: %s", e)

    def _on_send_key(self, ts, vk_snapshot):
        if self._in_cooldown():
            return
        name, hwnd, _ = get_foreground_info()
        target = self._current_target(name)
        if target is None:
            return
        self._last_send_ts = ts
        # 主路径 A: Enter 瞬间读输入框. 注意: 命中也不直接触发——
        # 中文输入法下按 Enter 只是把字母"上屏"进输入框(消息并未发送),
        # 直接触发会误报. 这里只登记候选, 由轮询在"输入框被清空"时确认发送后触发.
        v = self._read_input(target, hwnd)
        if v is not None:
            if v and matches_trigger(v):
                self._last_match_ts = time.time()
            return
        # 兜底 B: UIA 不可读时按键盘缓冲整串判定(组合态回车已在钩子里排除)
        if self.cfg["fallback_vk_buffer"] and matches_vk_buffer(vk_snapshot):
            self._fire("键盘缓冲 %s @%s" % ("".join(vk_snapshot), target.process))

    # ---------- 轮询 ----------
    def _poll_tick(self):
        name, hwnd, _ = get_foreground_info()
        target = self._current_target(name)
        if target is None:
            return
        if self._in_cooldown():
            return
        v = self._read_input(target, hwnd)
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
                self._fire("输入框清空+发送键 @%s" % target.process)

    # ---------- UIA ----------
    def _read_input(self, target, fg_hwnd):
        node = target.locate(fg_hwnd)
        if node is None:
            self._note_find_failure()
            return None
        v = target.read_text(node)
        if v is None:
            target.invalidate()
            self._note_find_failure()
            return None
        self._find_fail_streak = 0
        self._use_fallback = False
        return v

    def _note_find_failure(self):
        self._find_fail_streak += 1
        if self._find_fail_streak == 5:
            self._use_fallback = True
            log.info("[%s] 输入框连续定位失败, 启用键盘缓冲兜底",
                     self._target.process if self._target else "?")

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
