# -*- coding: utf-8 -*-
"""钉钉 MJ 彩蛋 - 桌面版入口.

在钉钉 PC 客户端中自己发送 mj/mjmj/MJ/MJMj 等组合时,
全屏播放带透明通道的蜘蛛侠动画(点击穿透, 不影响操作).

用法: python main.py  (或双击 启动彩蛋.bat / 钉钉MJ彩蛋.exe)
"""
import ctypes
import logging
import sys

from PyQt6.QtCore import QObject, pyqtSignal
from PyQt6.QtWidgets import QApplication, QMenu, QSystemTrayIcon

from mjegg import theme
from mjegg.about import AboutDialog, APP_NAME, APP_VERSION
from mjegg.autostart import is_enabled as autostart_enabled, set_enabled as set_autostart
from mjegg.config import load_config
from mjegg.overlay import draw_tray_icon_pixmap, spawn_egg_window
from mjegg.paths import app_dir
from mjegg.splash import Splash
from mjegg.watcher import Detector


def setup_logging():
    import os
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        handlers=[logging.FileHandler(os.path.join(app_dir(), "mj-dingtalk.log"), encoding="utf-8"),
                  logging.StreamHandler(sys.stdout)],
    )


def single_instance_guard():
    """同名互斥量防止多开."""
    kernel32 = ctypes.windll.kernel32
    kernel32.CreateMutexW.restype = ctypes.c_void_p
    h = kernel32.CreateMutexW(None, False, "MJ-DingTalk-Egg-Mutex")
    return bool(h) and kernel32.GetLastError() != 183  # ERROR_ALREADY_EXISTS


class TriggerBridge(QObject):
    """跨线程信号桥: 检测线程 -> Qt 主线程."""
    trigger = pyqtSignal()


def main():
    setup_logging()
    log = logging.getLogger("mjegg")
    if not single_instance_guard():
        log.warning("已有实例在运行, 退出")
        sys.exit(0)

    cfg = load_config()
    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)
    app.setApplicationName(APP_NAME)
    app.setApplicationDisplayName(APP_NAME)

    about_dialog_holder = {"dialog": None}
    app_icon = draw_tray_icon_pixmap()

    def show_about():
        dlg = about_dialog_holder["dialog"]
        if dlg is None:
            dlg = AboutDialog(app_icon)
            about_dialog_holder["dialog"] = dlg
        dlg.apply_theme()
        dlg.show()
        dlg.raise_()
        dlg.activateWindow()

    def _on_theme_changed(dark: bool):
        dlg = about_dialog_holder["dialog"]
        if dlg is not None and dlg.isVisible():
            dlg.apply_theme()

    theme.apply_app_theme()
    theme.watch_system_theme(_on_theme_changed)

    bridge = TriggerBridge()

    def play():
        if not tray_action_enable.isChecked():
            return
        # 每次播放新建窗口实例, 旧实例播完自动销毁
        spawn_egg_window(height_ratio=float(cfg["overlay_height_ratio"]),
                         volume=float(cfg.get("volume", 1.0))).play_on_foreground_screen()

    bridge.trigger.connect(play)

    detector = Detector(cfg, on_trigger=bridge.trigger.emit)
    detector.start()

    # ---------- 启动 splash: 屏幕中央渐显应用图标 ----------
    Splash(app_icon).run()

    # ---------- 托盘 ----------
    tray = QSystemTrayIcon()
    tray.setIcon(app_icon)
    tray.setToolTip(f"{APP_NAME} v{APP_VERSION}")

    menu = QMenu()
    tray_action_enable = menu.addAction("启用彩蛋")
    tray_action_enable.setCheckable(True)
    tray_action_enable.setChecked(bool(cfg["enabled"]))
    tray_action_enable.toggled.connect(lambda on: log.info("彩蛋%s", "启用" if on else "停用"))

    tray_action_test = menu.addAction("播放测试")
    tray_action_test.triggered.connect(play)

    tray_action_autostart = menu.addAction("开机自启")
    tray_action_autostart.setCheckable(True)
    tray_action_autostart.setChecked(autostart_enabled())
    tray_action_autostart.toggled.connect(
        lambda on: log.info("开机自启%s(%s)", "开" if on else "关",
                            "已写入" if set_autostart(on) else "失败"))

    tray_action_about = menu.addAction("关于")
    tray_action_about.triggered.connect(show_about)

    menu.addSeparator()
    tray_action_quit = menu.addAction("退出")
    tray_action_quit.triggered.connect(app.quit)

    tray.setContextMenu(menu)
    tray.activated.connect(
        lambda reason: menu.popup(tray.geometry().center())
        if reason == QSystemTrayIcon.ActivationReason.Trigger else None)

    if not tray_action_enable.isChecked():
        log.info("当前为停用状态, 托盘菜单可开启")
    tray.show()
    log.info("钉钉 MJ 彩蛋已启动 (触发词: mj/mjmj/... 只触发自己发送的)")

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
