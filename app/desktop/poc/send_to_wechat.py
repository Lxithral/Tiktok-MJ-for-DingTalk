# -*- coding: utf-8 -*-
"""把文件或一段文字发到微信「文件传输助手」—— 手机取包/收提醒用。

原理: 文件走剪贴板(CF_HDROP) + Ctrl+V 粘贴成附件; 文字直接按 Unicode 键输入。
两者都切到「文件传输助手」会话后回车发送。不依赖微信的任何私有接口,
走的是真实用户操作路径。

用法:
  python send_to_wechat.py <文件路径> [--chat 文件传输助手] [--no-send]
  python send_to_wechat.py --text "已 push"          # 发一段文字
  python send_to_wechat.py --check                   # 只看当前会话是不是目标会话

注意: 需要微信已登录并保持窗口可用; 发送过程会短暂抢焦点。
"""
import argparse
import ctypes
import ctypes.wintypes as wt
import os
import sys
import time

from PIL import ImageGrab

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32
try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except Exception:
    pass

CF_HDROP = 15
GMEM_MOVEABLE = 0x0002

VK_CONTROL = 0x11
VK_V = 0x56
VK_RETURN = 0x0D
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004


class DROPFILES(ctypes.Structure):
    _fields_ = [("pFiles", wt.DWORD), ("pt", wt.POINT),
                ("fNC", wt.BOOL), ("fWide", wt.BOOL)]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wt.WORD), ("wScan", wt.WORD), ("dwFlags", wt.DWORD),
                ("time", wt.DWORD), ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", wt.LONG), ("dy", wt.LONG), ("mouseData", wt.DWORD),
                ("dwFlags", wt.DWORD), ("time", wt.DWORD),
                ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]


class _U(ctypes.Union):
    _fields_ = [("ki", KEYBDINPUT), ("mi", MOUSEINPUT)]


class INPUT(ctypes.Structure):
    _fields_ = [("type", wt.DWORD), ("u", _U)]


# --- 64 位下句柄必须按指针声明, 否则会被截断 ---
user32.OpenClipboard.argtypes = [ctypes.c_void_p]
user32.SetClipboardData.argtypes = [wt.UINT, ctypes.c_void_p]
user32.SetClipboardData.restype = ctypes.c_void_p
kernel32.GlobalAlloc.argtypes = [wt.UINT, ctypes.c_size_t]
kernel32.GlobalAlloc.restype = ctypes.c_void_p
kernel32.GlobalLock.argtypes = [ctypes.c_void_p]
kernel32.GlobalLock.restype = ctypes.c_void_p
kernel32.GlobalUnlock.argtypes = [ctypes.c_void_p]


# ---------- 剪贴板 ----------
def set_clipboard_files(paths):
    """把一组文件放进剪贴板(CF_HDROP), 效果等同于在资源管理器里 Ctrl+C。"""
    files = "\0".join(os.path.abspath(p) for p in paths) + "\0\0"
    payload = files.encode("utf-16-le")

    df = DROPFILES()
    df.pFiles = ctypes.sizeof(DROPFILES)
    df.fWide = True
    header = ctypes.string_at(ctypes.byref(df), ctypes.sizeof(df))
    blob = header + payload

    if not user32.OpenClipboard(None):
        raise RuntimeError("打不开剪贴板")
    try:
        user32.EmptyClipboard()
        h = kernel32.GlobalAlloc(GMEM_MOVEABLE, len(blob))
        if not h:
            raise RuntimeError("GlobalAlloc 失败")
        p = kernel32.GlobalLock(h)
        ctypes.memmove(p, blob, len(blob))
        kernel32.GlobalUnlock(h)
        if not user32.SetClipboardData(CF_HDROP, h):
            raise RuntimeError("SetClipboardData 失败")
    finally:
        user32.CloseClipboard()


# ---------- 输入 ----------
def _key(vk, up=False, unicode_ch=None):
    i = INPUT()
    i.type = 1
    if unicode_ch is None:
        i.u.ki = KEYBDINPUT(vk, 0, KEYEVENTF_KEYUP if up else 0, 0, None)
    else:
        i.u.ki = KEYBDINPUT(0, ord(unicode_ch),
                            KEYEVENTF_UNICODE | (KEYEVENTF_KEYUP if up else 0), 0, None)
    user32.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))


def tap(vk, hold=0.04):
    _key(vk, False)
    time.sleep(hold)
    _key(vk, True)
    time.sleep(hold)


def type_text(text):
    for ch in text:
        _key(0, False, ch)
        time.sleep(0.02)
        _key(0, True, ch)
        time.sleep(0.02)


def paste():
    _key(VK_CONTROL, False)
    time.sleep(0.05)
    tap(VK_V)
    _key(VK_CONTROL, True)
    time.sleep(0.1)


def click(x, y):
    user32.SetCursorPos(x, y)
    time.sleep(0.2)
    for f in (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP):
        i = INPUT()
        i.type = 0
        i.u.mi = MOUSEINPUT(x, y, 0, f, 0, None)
        user32.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))
        time.sleep(0.08)


# ---------- 窗口 ----------
def proc_name_of(pid):
    h = kernel32.OpenProcess(0x1000, False, pid)
    if not h:
        return ""
    buf = ctypes.create_unicode_buffer(1024)
    size = wt.DWORD(1024)
    nm = ""
    if kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
        nm = buf.value.split("\\")[-1]
    kernel32.CloseHandle(h)
    return nm


def wechat_window():
    res = []
    EP = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        pid = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if proc_name_of(pid.value).lower() != "weixin.exe":
            return True
        if not user32.IsWindowVisible(hwnd):
            return True
        rc = wt.RECT()
        user32.GetWindowRect(hwnd, ctypes.byref(rc))
        area = (rc.right - rc.left) * (rc.bottom - rc.top)
        if area > 100000:
            res.append((hwnd, area))
        return True
    user32.EnumWindows(EP(cb), 0)
    return max(res, key=lambda x: x[1])[0] if res else 0


def find_by_class(node, needle, depth=0, hits=None, max_depth=30):
    if hits is None:
        hits = []
    if depth > max_depth or hits:
        return hits
    try:
        children = node.GetChildren()
    except Exception:
        return hits
    for c in children:
        try:
            if needle in (c.ClassName or ""):
                hits.append(c)
                return hits
        except Exception:
            pass
        find_by_class(c, needle, depth + 1, hits, max_depth)
    return hits


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("path", nargs="?", help="要发送的文件")
    ap.add_argument("--text", default=None, help="要发送的一段文字(与文件二选一)")
    ap.add_argument("--chat", default="文件传输助手", help="目标会话名")
    ap.add_argument("--check", action="store_true", help="只检查当前会话")
    ap.add_argument("--no-send", action="store_true", help="粘贴后不按回车")
    ap.add_argument("--shot", default="", help="发送后截图保存路径")
    args = ap.parse_args()

    import uiautomation as uia
    uia.SetGlobalSearchTimeout(1.0)

    hwnd = wechat_window()
    if not hwnd:
        print("找不到微信窗口(Weixin.exe 是否在运行?)")
        return 1
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.8)

    win = uia.ControlFromHandle(hwnd)
    edits = find_by_class(win, "mmui::ChatInputField")
    if not edits:
        print("找不到微信聊天输入框")
        return 1
    edit = edits[0]
    current = (edit.Name or "").strip()
    print("当前会话: %r" % current)
    if args.check:
        return 0

    # 切到目标会话: 用搜索框, 比在会话列表里找条目稳
    if args.chat not in current:
        print("切换到 %r ..." % args.chat)
        search = find_by_class(win, "mmui::XValidatorTextEdit")
        if not search:
            print("找不到搜索框, 无法切会话")
            return 1
        r = search[0].BoundingRectangle
        click((r.left + r.right) // 2, (r.top + r.bottom) // 2)
        time.sleep(0.4)
        # 清空搜索框
        for _ in range(20):
            tap(0x08)
        time.sleep(0.2)
        type_text(args.chat)
        time.sleep(1.2)
        tap(VK_RETURN)
        time.sleep(1.5)
        win = uia.ControlFromHandle(hwnd)
        edits = find_by_class(win, "mmui::ChatInputField")
        if not edits:
            print("切会话后找不到输入框")
            return 1
        edit = edits[0]
        print("切换后会话: %r" % (edit.Name or ""))

    # ---------- 文字模式 ----------
    if args.text is not None:
        r = edit.BoundingRectangle
        click((r.left + r.right) // 2, (r.top + r.bottom) // 2)
        time.sleep(0.5)
        # 先清掉可能残留的草稿
        for _ in range(30):
            tap(0x08)
        time.sleep(0.2)
        print("输入文字: %s" % args.text)
        type_text(args.text)
        time.sleep(0.8)
        if args.no_send:
            print("已输入, 按 --no-send 要求不发送")
            return 0
        tap(VK_RETURN)
        time.sleep(1.5)
        if args.shot:
            ImageGrab.grab().save(args.shot)
            print("已截图 %s" % args.shot)
        win = uia.ControlFromHandle(hwnd)
        edits = find_by_class(win, "mmui::ChatInputField")
        if edits:
            print("发送后输入框: %r" % (edits[0].GetValuePattern().Value,))
        return 0

    if args.path is None:
        print("没有指定要发送的文件(或用 --text 发文字)")
        return 1
    path = os.path.abspath(args.path)
    if not os.path.exists(path):
        print("文件不存在: %s" % path)
        return 1
    print("文件: %s (%.1f MB)" % (path, os.path.getsize(path) / 1048576))

    set_clipboard_files([path])
    print("已把文件放进剪贴板")

    r = edit.BoundingRectangle
    click((r.left + r.right) // 2, (r.top + r.bottom) // 2)
    time.sleep(0.5)
    print("粘贴 (Ctrl+V) ...")
    paste()
    time.sleep(2.5)

    if args.no_send:
        print("已粘贴, 按 --no-send 要求不发送")
        return 0

    print("回车发送 ...")
    tap(VK_RETURN)
    time.sleep(3.0)

    if args.shot:
        ImageGrab.grab().save(args.shot)
        print("已截图 %s" % args.shot)

    # 发送后输入框应变空(附件已被发出)
    win = uia.ControlFromHandle(hwnd)
    edits = find_by_class(win, "mmui::ChatInputField")
    if edits:
        print("发送后输入框: %r" % (edits[0].GetValuePattern().Value,))
    return 0


if __name__ == "__main__":
    sys.exit(main())
