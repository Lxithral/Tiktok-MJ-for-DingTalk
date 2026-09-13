# -*- coding: utf-8 -*-
"""WH_KEYBOARD_LL 低级键盘钩子线程.

只做三件事, 保证回调足够快(不触碰 UIA/COM):
  1. 把"发送键"事件(Enter / Alt+S)的时间戳投入队列, 供 watcher 消费;
  2. 维护可打印键的滚动 VK 缓冲(路径 B 兜底用);
  3. Escape/Backspace 维护缓冲语义.
"""
import ctypes
import ctypes.wintypes as wintypes
import queue
import threading
import time

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
    """低级键盘钩子线程. send_events: 发送键事件队列(线程安全). vk_buffer: 最近按键."""

    def __init__(self, ignore_injected=True, buffer_size=16):
        super().__init__(daemon=True, name="KeyHookThread")
        self.send_events = queue.Queue()
        self.vk_buffer = []
        self.event_count = 0  # 收到的键盘事件总数(调试/测试用)
        self._buffer_size = buffer_size
        self._ignore_injected = ignore_injected
        self._hook = None
        self._proc = None  # 持有引用防 GC

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
                if not (self._ignore_injected and injected):
                    self._handle(info.vkCode, info.scanCode, w_param, bool(info.flags & LLKHF_ALTDOWN))
                del key_up
        except Exception as e:  # 绝不让异常冒泡进钩子
            print(f"[keyhook] callback error: {e}", flush=True)
        return user32.CallNextHookEx(None, n_code, w_param, l_param)

    def _handle(self, vk, scan, w_param, alt_down):
        key_down = w_param in (WM_KEYDOWN, WM_SYSKEYDOWN)
        if not key_down:
            return
        # 注入的按键可能不带真实 vkCode(如 0xE7), 用扫描码还原可打印字符
        if 0x41 <= vk <= 0x5A or 0x30 <= vk <= 0x39:
            ch = chr(vk)
        else:
            ch = _SCAN_TO_CHAR.get(scan)  # 真实键盘 Set-1 硬件扫描码
            if ch is None:
                if 0x61 <= scan <= 0x7A:  # 部分注入实现用 ASCII 作扫描码
                    ch = chr(scan).upper()
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
            # 把缓冲快照随事件一起入队(路径 B 兜底用), 然后清空缓冲
            # scan 兼容: 0x0D=部分注入实现, 0x1C=Set-1 硬件回车码
            self.send_events.put((time.time(), list(self.vk_buffer)))
            self.vk_buffer.clear()
        elif vk == VK_S and alt_down:  # Alt+S 发送热键
            self.send_events.put((time.time(), list(self.vk_buffer)))
        elif vk == VK_BACK:
            if self.vk_buffer:
                self.vk_buffer.pop()
        elif vk == VK_ESCAPE:
            self.vk_buffer.clear()
