# -*- coding: utf-8 -*-
"""深浅色主题: 跟随系统并实时切换.

用 Qt6 的 QStyleHints.colorScheme 检测, colorSchemeChanged 信号实时响应;
暗色时对 QApplication 应用 Fusion 风格 + 深色 QPalette(托盘菜单随之变暗),
亮色时恢复默认.
"""
import logging

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QColor, QPalette
from PyQt6.QtWidgets import QApplication, QStyleFactory

log = logging.getLogger("mjegg.theme")

# 两套配色 (用于自绘窗口的样式表)
LIGHT = {
    "window_bg": "#ffffff", "card_bg": "#f4f5f7", "text": "#1a1a1a",
    "subtext": "#6b6f76", "line": "#e3e5e8", "accent": "#e63c3c", "hover": "#c93030",
}
DARK = {
    "window_bg": "#1e1f24", "card_bg": "#2a2c33", "text": "#f2f3f5",
    "subtext": "#9aa0a8", "line": "#3a3d45", "accent": "#ff5c5c", "hover": "#e04b4b",
}


def is_dark() -> bool:
    hints = QApplication.instance().styleHints() if QApplication.instance() else None
    if hints is None:
        return False
    try:
        from PyQt6.QtCore import Qt as _Qt
        return hints.colorScheme() == _Qt.ColorScheme.Dark
    except Exception:
        return False


def palette_dict() -> dict:
    return DARK if is_dark() else LIGHT


def apply_app_theme():
    """对整个 QApplication 应用跟随系统的配色(主要影响托盘菜单等原生控件)."""
    app = QApplication.instance()
    if app is None:
        return
    if is_dark():
        app.setStyle(QStyleFactory.create("Fusion"))
        pal = QPalette()
        bg, base, text, sub = QColor("#1e1f24"), QColor("#26282e"), QColor("#f2f3f5"), QColor("#9aa0a8")
        pal.setColor(QPalette.ColorRole.Window, bg)
        pal.setColor(QPalette.ColorRole.WindowText, text)
        pal.setColor(QPalette.ColorRole.Base, base)
        pal.setColor(QPalette.ColorRole.AlternateBase, bg)
        pal.setColor(QPalette.ColorRole.Text, text)
        pal.setColor(QPalette.ColorRole.PlaceholderText, sub)
        pal.setColor(QPalette.ColorRole.Button, base)
        pal.setColor(QPalette.ColorRole.ButtonText, text)
        pal.setColor(QPalette.ColorRole.ToolTipBase, base)
        pal.setColor(QPalette.ColorRole.ToolTipText, text)
        pal.setColor(QPalette.ColorRole.Highlight, QColor("#e63c3c"))
        pal.setColor(QPalette.ColorRole.HighlightedText, QColor("#ffffff"))
        pal.setColor(QPalette.ColorGroup.Disabled, QPalette.ColorRole.Text, QColor("#5c6066"))
        pal.setColor(QPalette.ColorGroup.Disabled, QPalette.ColorRole.ButtonText, QColor("#5c6066"))
        app.setPalette(pal)
    else:
        app.setPalette(app.style().standardPalette())
    log.info("应用主题: %s", "深色" if is_dark() else "浅色")


def watch_system_theme(on_change):
    """监听系统深浅色切换, 回调 on_change(is_dark: bool). 返回连接对象."""
    app = QApplication.instance()
    if app is None:
        return None
    hints = app.styleHints()

    def _slot(scheme):
        apply_app_theme()
        try:
            on_change(scheme == Qt.ColorScheme.Dark)
        except Exception:
            log.exception("主题切换回调异常")

    return hints.colorSchemeChanged.connect(_slot)
