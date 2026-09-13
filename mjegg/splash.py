# -*- coding: utf-8 -*-
"""启动 splash: 程序启动时在屏幕中央短暂显示应用图标与版本号.

实现说明:
- 必须用"全屏透明窗口+居中卡片"(与彩蛋 overlay 同构): 实测小尺寸窗口 show()
  在打包后/开发环境都不会上屏。
- 必须持有窗口引用(模块级 _current): 无父组件的窗口若无人引用会被 Python GC
  立即销毁, 表现为"日志显示执行了但屏幕上永远看不到"(实测踩坑)。
- 不要用 QGraphicsOpacityEffect 做淡入淡出: 打包环境动画不推进, 初始透明度 0
  会完全不可见。
- 窗口设置点击穿透, 显示期间不影响鼠标操作。
"""
import logging

from PyQt6.QtCore import Qt, QTimer, QPoint
from PyQt6.QtGui import QCursor, QGuiApplication
from PyQt6.QtWidgets import QWidget, QVBoxLayout, QLabel

from .about import APP_NAME, APP_VERSION
from .overlay import _make_click_through
from . import theme

log = logging.getLogger("mjegg.splash")

_current = None  # 持有当前 splash 引用, 防止 GC 秒杀窗口


class Splash(QWidget):
    """全屏透明置顶窗口, 屏幕中央显示图标卡片, 约 2.6 秒后自动关闭."""

    def __init__(self, app_icon):
        super().__init__(None,
                         Qt.WindowType.FramelessWindowHint
                         | Qt.WindowType.Tool
                         | Qt.WindowType.WindowStaysOnTopHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating)

        c = theme.palette_dict()
        self._card = QWidget(self)
        self._card.setFixedSize(300, 240)
        self._card.setStyleSheet(
            f"background: {c['card_bg']}; border-radius: 16px;"
            f"border: 1px solid {c['line']};")
        v = QVBoxLayout(self._card)
        v.setContentsMargins(0, 18, 0, 14)
        v.setSpacing(6)
        icon_label = QLabel()
        icon_label.setPixmap(app_icon.pixmap(112, 112))
        icon_label.setScaledContents(True)
        icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        name = QLabel(APP_NAME)
        name.setStyleSheet(f"color: {c['text']}; font-size: 20px; font-weight: 700; background: transparent;")
        name.setAlignment(Qt.AlignmentFlag.AlignCenter)
        ver = QLabel(f"v{APP_VERSION}")
        ver.setStyleSheet(f"color: {c['subtext']}; font-size: 12px; background: transparent;")
        ver.setAlignment(Qt.AlignmentFlag.AlignCenter)
        v.addStretch(1)
        v.addWidget(icon_label)
        v.addWidget(name)
        v.addWidget(ver)
        v.addStretch(1)

    def resizeEvent(self, ev):
        # 全屏窗口内容卡片保持屏幕居中
        self._card.move((self.width() - self._card.width()) // 2,
                        (self.height() - self._card.height()) // 2)
        super().resizeEvent(ev)

    def run(self):
        global _current
        _current = self
        screen = QGuiApplication.screenAt(QCursor.pos()) or QGuiApplication.primaryScreen()
        self.setGeometry(screen.geometry())
        self.showFullScreen()
        _make_click_through(int(self.winId()))
        log.info("splash: 屏幕中央显示图标 %s", self._card.geometry())
        QTimer.singleShot(2600, self._close)
        return self

    def _close(self):
        global _current
        self.close()
        _current = None
