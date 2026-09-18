# -*- coding: utf-8 -*-
"""实验: 找出能唤醒 QQ NT(Chromium)主窗口无障碍树的方法.

依次尝试多种激活策略, 每种之后测量主窗口 UIA 子树的节点数:
  A. SetForegroundWindow + 向 render widget 发 WM_GETOBJECT
  B. 向顶层窗口发 WM_GETOBJECT
  C. 注册 UIA 事件处理器(让 UiaClientsAreListening() 变真) + 等待
  D. SPI_SETSCREENREADER 系统标志 + WM_SETTINGCHANGE + 等待(测完恢复)
"""
import ctypes
import ctypes.wintypes as wt
import time

import uiautomation as uia
import comtypes.client

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

WM_GETOBJECT = 0x003D
WM_SETTINGCHANGE = 0x001A
OBJID_CLIENT = 0xFFFFFFFC
SPI_GETSCREENREADER = 0x0046
SPI_SETSCREENREADER = 0x0047
SPIF_UPDATEINIFILE = 0x01
SPIF_SENDCHANGE = 0x02


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


def enum_top():
    res = []
    EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        pid = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        cls = ctypes.create_unicode_buffer(256)
        user32.GetClassNameW(hwnd, cls, 256)
        rc = wt.RECT()
        user32.GetWindowRect(hwnd, ctypes.byref(rc))
        res.append((hwnd, pid.value, cls.value,
                    (rc.left, rc.top, rc.right, rc.bottom),
                    bool(user32.IsWindowVisible(hwnd))))
        return True
    user32.EnumWindows(EnumProc(cb), 0)
    return res


def children_of(parent):
    out = []
    EnumChild = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

    def cb(hwnd, _):
        cls = ctypes.create_unicode_buffer(256)
        user32.GetClassNameW(hwnd, cls, 256)
        out.append((hwnd, cls.value))
        return True
    user32.EnumChildWindows(parent, EnumChild(cb), 0)
    return out


def tree_size(win):
    n = [0]

    def walk(node, depth):
        if depth > 22:
            return
        try:
            ch = node.GetChildren()
        except Exception:
            return
        for c in ch:
            n[0] += 1
            if n[0] > 3000:
                return
            walk(c, depth + 1)
    walk(win, 1)
    return n[0]


def edits_of(win, limit=40):
    res = []

    def walk(node, depth):
        if depth > 22 or len(res) >= limit:
            return
        try:
            ch = node.GetChildren()
        except Exception:
            return
        for c in ch:
            try:
                ct = c.ControlTypeName
                if "Edit" in ct or "Document" in ct:
                    r = c.BoundingRectangle
                    res.append((depth, ct, c.ClassName, (c.Name or "")[:40],
                                (r.left, r.top, r.right, r.bottom)))
            except Exception:
                pass
            walk(c, depth + 1)
    walk(win, 1)
    return res


def main():
    uia.SetGlobalSearchTimeout(0.5)
    # 主窗口 = QQ 可见的最大 Chrome_WidgetWin_1
    cands = [w for w in enum_top()
             if proc_name_of(w[1]).lower() == "qq.exe"
             and w[2].startswith("Chrome_WidgetWin_1") and w[4]]
    main_win = max(cands, key=lambda w: (w[3][2] - w[3][0]) * (w[3][3] - w[3][1]))
    print("主窗口 hwnd=%s rect=%s" % (main_win[0], main_win[3]))
    rw = [c for c in children_of(main_win[0]) if "Chrome_RenderWidgetHostHWND" in c[1]]
    print("render widget 子窗口: %s" % rw)

    def measure(tag):
        try:
            win = uia.ControlFromHandle(main_win[0])
            n = tree_size(win)
            e = edits_of(win)
        except Exception as ex:
            print("  [%s] 测量失败: %s" % (tag, ex))
            return 0
        print("  [%s] 节点数=%d  可编辑=%d" % (tag, n, len(e)))
        for x in e[:6]:
            print("      %s" % (x,))
        return n

    print("\n--- 基线 ---")
    measure("基线")

    print("\n--- A. 前台 + WM_GETOBJECT(render widget) ---")
    user32.SetForegroundWindow(main_win[0])
    time.sleep(0.5)
    for h, c in rw:
        user32.SendMessageW(h, WM_GETOBJECT, 0, ctypes.c_void_p(OBJID_CLIENT))
    time.sleep(2.0)
    measure("A")

    print("\n--- B. WM_GETOBJECT(顶层窗口) ---")
    user32.SendMessageW(main_win[0], WM_GETOBJECT, 0, ctypes.c_void_p(OBJID_CLIENT))
    time.sleep(1.5)
    measure("B")

    print("\n--- C. 注册 UIA 事件处理器 + 等待 8s ---")
    try:
        root = uia.GetRootControl()
        handler = comtypes.client.CreateObject(
            "{FF48DBA4-60EF-4201-AA87-54103EEF594E}",  # CUIAutomation
            interface=comtypes.client.GetModule("UIAutomationCore.dll")
            if False else None) if False else None
        # 直接用 uiautomation 自带的事件注册接口
        import uiautomation.uiautomation as _u
        cu = _u._AutomationClient.instance().UIAutomation
        from comtypes.gen import UIAutomationClient as UIA
        cu.AddStructureChangedEventHandler(
            root, UIA.TreeScope_Subtree,
            ctypes.cast(None, ctypes.c_void_p), None)
        print("  已注册 StructureChanged 处理器")
    except Exception as e:
        print("  注册事件处理器失败: %s" % e)
    for i in range(4):
        time.sleep(2.0)
        print("  ...等待 %ds" % ((i + 1) * 2))
    measure("C")

    print("\n--- D. SPI_SETSCREENREADER + WM_SETTINGCHANGE ---")
    old = wt.BOOL()
    user32.SystemParametersInfoW(SPI_GETSCREENREADER, 0, ctypes.byref(old), 0)
    print("  原 screenreader 标志 = %s" % bool(old.value))
    user32.SystemParametersInfoW(
        SPI_SETSCREENREADER, 1, None, SPIF_SENDCHANGE)
    HWND_BROADCAST = 0xFFFF
    user32.SendMessageTimeoutW(HWND_BROADCAST, WM_SETTINGCHANGE, 0, 0, 2, 3000, None)
    for i in range(4):
        time.sleep(2.0)
        print("  ...等待 %ds" % ((i + 1) * 2))
    n = measure("D")
    # 恢复
    user32.SystemParametersInfoW(
        SPI_SETSCREENREADER, 1 if old.value else 0, None, SPIF_SENDCHANGE)
    print("  已恢复 screenreader 标志 = %s" % bool(old.value))

    print("\n结论: D 之后节点数=%d" % n)


if __name__ == "__main__":
    main()
