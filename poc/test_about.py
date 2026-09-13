# -*- coding: utf-8 -*-
"""关于窗口验证: 深浅色跟随系统 + 实时切换 (临时翻转注册表主题键, 结束恢复)."""
import os
import sys
sys.path.insert(0, "..")

import winreg
from PyQt6.QtCore import QTimer
from PyQt6.QtWidgets import QApplication

from mjegg import theme
from mjegg.about import AboutDialog
from mjegg.overlay import draw_tray_icon_pixmap

REG = r"SOFTWARE\Microsoft\Windows\CurrentVersion\Themes\Personalize"


def read_light():
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, REG) as k:
        v, _ = winreg.QueryValueEx(k, "AppsUseLightTheme")
        return v


def write_light(v):
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, REG, 0, winreg.KEY_SET_VALUE) as k:
        winreg.SetValueEx(k, "AppsUseLightTheme", 0, winreg.REG_DWORD, v)
    # 直写注册表不会通知应用, 必须广播设置变更(系统改主题时就是广播这个)
    import ctypes
    HWND_BROADCAST, WM_SETTINGCHANGE, SMTO_ABORTIFHUNG = 0xFFFF, 0x001A, 0x0002
    ctypes.windll.user32.SendMessageTimeoutW(
        HWND_BROADCAST, WM_SETTINGCHANGE, 0, "ImmersiveColorSet", SMTO_ABORTIFHUNG, 1000, None)


def main():
    app = QApplication(sys.argv)
    theme.apply_app_theme()
    icon = draw_tray_icon_pixmap()
    dlg = AboutDialog(icon)
    dlg.show()
    theme.watch_system_theme(lambda dark: dlg.apply_theme())  # 与 main.py 相同的接线
    out = os.path.dirname(os.path.abspath(__file__))

    original = read_light()
    print("系统初始 AppsUseLightTheme =", original, "Qt判定深色:", theme.is_dark(), flush=True)

    state = {"step": 0}

    def step():
        state["step"] += 1
        s = state["step"]
        if s == 1:  # 初始主题截图
            dlg.grab().save(os.path.join(out, "about_initial.png"))
            print("saved about_initial.png", flush=True)
            write_light(0 if original else 1)  # 翻转系统主题
            print("翻转系统主题 ->", "深色" if original else "浅色", flush=True)
        elif s == 2:  # 等待 Qt 收到 colorSchemeChanged 后再截图
            dlg.grab().save(os.path.join(out, "about_toggled.png"))
            print("saved about_toggled.png, Qt判定深色:", theme.is_dark(), flush=True)
            write_light(original)  # 恢复
            print("恢复系统主题 =", original, flush=True)
        elif s == 3:
            dlg.grab().save(os.path.join(out, "about_restored.png"))
            print("saved about_restored.png, Qt判定深色:", theme.is_dark(), flush=True)
            app.quit()

    t = QTimer()
    t.timeout.connect(step)
    t.start(2000)
    app.exec()


if __name__ == "__main__":
    main()
