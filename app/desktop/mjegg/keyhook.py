# -*- coding: utf-8 -*-
"""WH_KEYBOARD_LL 低级键盘钩子线程.

只做四件事, 保证回调足够快(不触碰 UIA/COM):
  1. 把"发送键"事件(Enter / Alt+S)的时间戳投入队列, 供 watcher 消费;
  2. 维护可打印键的滚动 VK 缓冲(路径 B 兜底用);
  3. Escape/Backspace 维护缓冲语义;
  4. 识别"输入法组合态下的回车"——那只是把候选词上屏, 消息并未发出,
     此时把缓冲搬到 pending(已上屏内容), 不入队、不清零, 等真正的发送键到来.

前台窗口切换时缓冲清零: 缓冲的语义是"当前输入框里被敲进去的内容",
换了窗口就不该再沿用上一个窗口的按键历史.

## 注入按键策略 (injected_key_policy)

**Windows 屏幕键盘 (osk.exe) 和触屏键盘 (TabTip) 都是通过 SendInput 注入按键的**,
实测这类按键带 `LLKHF_INJECTED(0x10)` 标志, 所以"一律忽略注入按键"会把屏幕键盘
也一起挡掉. 但按键精灵之类的宏工具同样走 SendInput, 又不能全放开. 折中策略:

| 取值 | 行为 |
|---|---|
| `ignore`   | 从不接受注入按键(老行为, 屏幕键盘不可用) |
| `unsigned` | 只接受 `dwExtraInfo == 0` 的注入(实测屏幕键盘/触屏键盘注入时不带签名) |
| `auto`     | **默认**: `unsigned`, 且当检测到屏幕键盘进程正在运行时放宽为接受全部注入 |
| `allow`    | 全部接受 |

`auto` 里的"屏幕键盘进程"由 watcher 线程定期探测后写入 `screen_keyboard_active`,
钩子回调只读一个布尔量, 不做任何系统调用, 保证回调足够快.
"""
import ctypes
import ctypes.wintypes as wintypes
import logging
import queue
import threading
import time

from . import ime

log = logging.getLogger("mjegg.keyhook")

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32


WH_KEYBOARD_LL = 13
WM_KEYDOWN = 0x0100
WM_SYSKEYDOWN = 0x0104

LLKHF_UP = 0x80
LLKHF_INJECTED = 0x10
LLKHF_ALTDOWN = 0x20

VK_BACK = 0x08
VK_ESCAPE = 0x1B
VK_RETURN = 0x0D
VK_S = 0x53
VK_CONTROL = 0x11
VK_MENU = 0x12  # Alt

HOOKPROC = ctypes.WINFUNCTYPE(ctypes.c_ssize_t, ctypes.c_int, wintypes.WPARAM, wintypes.LPARAM)

# 注入按键 vkCode 缺失时按 Set-1 扫描码还原字母/数字(兜底路径用)
_SCAN_TO_CHAR = {
    0x02: "1", 0x03: "2", 0x04: "3", 0x05: "4", 0x06: "5", 0x07: "6", 0x08: "7", 0x09: "8",
    0x0A: "9", 0x0B: "0", 0x10: "Q", 0x11: "W", 0x12: "E", 0x13: "R", 0x14: "T", 0x15: "Y",
    0x16: "U", 0x17: "I", 0x18: "O", 0x19: "P", 0x1E: "A", 0x1F: "S", 0x20: "D", 0x21: "F",
    0x22: "G", 0x23: "H", 0x24: "J", 0x25: "K", 0x26: "L", 0x2C: "Z", 0x2D: "X", 0x2E: "C",
    0x2F: "V", 0x30: "B", 0x31: "N", 0x32: "M",
}

# 不设置 argtypes 时 ctypes 会把 64 位 lParam 按 c_int 转换, 抛 OverflowError
user32.CallNextHookEx.argtypes = [ctypes.c_void_p, ctypes.c_int, wintypes.WPARAM, wintypes.LPARAM]
user32.CallNextHookEx.restype = ctypes.c_ssize_t
user32.SetWindowsHookExW.argtypes = [ctypes.c_int, HOOKPROC, ctypes.c_void_p, wintypes.DWORD]
user32.UnhookWindowsHookEx.argtypes = [ctypes.c_void_p]


class KBDLLHOOKSTRUCT(ctypes.Structure):
    _fields_ = [
        ("vkCode", wintypes.DWORD),
        ("scanCode", wintypes.DWORD),
        ("flags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_size_t),
    ]


class KeyHookThread(threading.Thread):
    """低级键盘钩子线程.

    send_events: 发送键事件队列, 元素为 (时间戳, 待发送文本的 VK 快照)
    vk_buffer  : 当前输入框内已敲入的可打印字符
    """

    def __init__(self, injected_policy="auto", ime_aware=True, buffer_size=64):
        super().__init__(daemon=True, name="KeyHookThread")
        self.send_events = queue.Queue()
        self.vk_buffer = []
        self.vk_pending = []      # 已被输入法"上屏"的内容(组合态回车提交的部分)
        self.event_count = 0      # 收到的键盘事件总数(调试/测试用)
        # 由 watcher 线程定期刷新: 屏幕键盘(osk/TabTip)是否正开着
        self.screen_keyboard_active = False
        self._buffer_size = buffer_size
        self._injected_policy = injected_policy if injected_policy in (
            "ignore", "unsigned", "auto", "allow") else "auto"
        self._ime_aware = ime_aware
        self._hook = None
        self._proc = None         # 持有引用防 GC
        self._fg_hwnd = 0
        self._ignored_logged = 0
        self._last_ignored_log = 0.0


    def run(self):
        self._proc = HOOKPROC(self._callback)
        user32.SetWindowsHookExW.restype = ctypes.c_void_p
        self._hook = user32.SetWindowsHookExW(WH_KEYBOARD_LL, self._proc, None, 0)
        if not self._hook:
            err = kernel32.GetLastError()
            print(f"[keyhook] SetWindowsHookExW 失败, err={err}", flush=True)
            return
        msg = wintypes.MSG()
        while user32.GetMessageW(ctypes.byref(msg), None, 0, 0) > 0:
            pass
        user32.UnhookWindowsHookEx(self._hook)

    def _callback(self, n_code, w_param, l_param):
        try:
            if n_code >= 0:
                self.event_count += 1
                info = ctypes.cast(ctypes.c_void_p(l_param), ctypes.POINTER(KBDLLHOOKSTRUCT)).contents
                key_up = bool(info.flags & LLKHF_UP)
                injected = bool(info.flags & LLKHF_INJECTED)
                if self._should_process(injected, info.dwExtraInfo):
                    self._handle(info.vkCode, info.scanCode, w_param, bool(info.flags & LLKHF_ALTDOWN))
                del key_up
        except Exception as e:  # 绝不让异常冒泡进钩子
            print(f"[keyhook] callback error: {e}", flush=True)
        return user32.CallNextHookEx(None, n_code, w_param, l_param)

    # ---------- 注入按键策略 ----------
    def _should_process(self, injected, extra_info) -> bool:
        if not injected:
            return True
        policy = self._injected_policy
        if policy == "ignore":
            self._note_ignored(injected, extra_info)
            return False
        if policy == "allow":
            return True
        if policy == "unsigned":
            ok = extra_info == 0
        else:  # auto: 屏幕键盘开着就全收, 否则只收无签名的
            ok = extra_info == 0 or self.screen_keyboard_active
        if not ok:
            self._note_ignored(injected, extra_info)
        return ok

    def _note_ignored(self, injected, extra_info):
        """忽略注入按键时留一条可诊断的日志(限频, 避免拖慢钩子回调)."""
        now = time.time()
        if self._ignored_logged >= 5 or now - self._last_ignored_log < 10.0:
            return
        self._ignored_logged += 1
        self._last_ignored_log = now
        log.info("已忽略注入按键 (策略=%s, dwExtraInfo=0x%X); "
                 "若要用屏幕键盘, 把 config.json 的 injected_key_policy 设为 auto/allow",
                 self._injected_policy, extra_info)


    # ---------- 内部 ----------
    def _reset_buffers(self):
        self.vk_buffer.clear()
        self.vk_pending.clear()

    def _sync_foreground(self):
        """前台窗口变了就清空缓冲(缓冲只对当前输入框有意义)."""
        try:
            fg = user32.GetForegroundWindow()
        except Exception:
            return
        if fg != self._fg_hwnd:
            self._fg_hwnd = fg
            self._reset_buffers()

    def _snapshot(self):
        return list(self.vk_pending) + list(self.vk_buffer)

    def _handle(self, vk, scan, w_param, alt_down):
        key_down = w_param in (WM_KEYDOWN, WM_SYSKEYDOWN)
        if not key_down:
            return
        self._sync_foreground()
        # 注入的按键可能不带真实 vkCode(如 0xE7), 用扫描码还原可打印字符
        if 0x41 <= vk <= 0x5A or 0x30 <= vk <= 0x39:
            ch = chr(vk)
        else:
            ch = _SCAN_TO_CHAR.get(scan)  # 真实键盘 Set-1 硬件扫描码
            if ch is None:
                # 部分注入实现(含屏幕键盘的部分路径)把 ASCII 码直接塞进 scanCode
                if 0x61 <= scan <= 0x7A:
                    ch = chr(scan).upper()
                elif 0x41 <= scan <= 0x5A:
                    ch = chr(scan)
                elif 0x30 <= scan <= 0x39:
                    ch = chr(scan)
        if ch is not None:
            if user32.GetAsyncKeyState(VK_CONTROL) & 0x8000 or user32.GetAsyncKeyState(VK_MENU) & 0x8000:
                return  # Ctrl/Alt 组合键是快捷键而非打字
            self.vk_buffer.append(ch.upper())
            if len(self.vk_buffer) > self._buffer_size:
                del self.vk_buffer[: len(self.vk_buffer) - self._buffer_size]
            return
        if vk == VK_RETURN or scan in (0x0D, 0x1C) and vk in (0, 0xE7):
            # scan 兼容: 0x0D=部分注入实现, 0x1C=Set-1 硬件回车码
            if self._ime_aware and ime.has_composition(self._fg_hwnd or None):
                # 回车被输入法吃掉: 候选词上屏, 消息并未发出.
                # 把内容搬到 pending 并保留, 等用户再按一次回车真正发送.
                self.vk_pending.extend(self.vk_buffer)
                self.vk_buffer.clear()
                if len(self.vk_pending) > self._buffer_size:
                    del self.vk_pending[: len(self.vk_pending) - self._buffer_size]
                return
            # 真正的发送键: 把"输入框里的完整内容"随事件入队(路径 B 兜底用)
            self.send_events.put((time.time(), self._snapshot()))
            self._reset_buffers()
        elif vk == VK_S and alt_down:  # Alt+S 发送热键
            self.send_events.put((time.time(), self._snapshot()))
            self._reset_buffers()
        elif vk == VK_BACK:
            if self.vk_buffer:
                self.vk_buffer.pop()
            elif self.vk_pending:
                self.vk_pending.pop()
        elif vk == VK_ESCAPE:
            self._reset_buffers()
