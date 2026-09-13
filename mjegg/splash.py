# -*- coding: utf-8 -*-
"""启动 splash: 程序启动时在屏幕中央短暂渐显应用图标与版本号."""
import logging

from PyQt6.QtCore import Qt, QTimer, QPropertyAnimation, QPoint
from PyQt6.QtGui import QCursor, QGuiApplication
from PyQt6.QtWidgets import QWidget, QVBoxLayout, QLabel, QGraphicsOpacityEffect

from .about import APP_NAME, APP_VERSION
from . import theme

log = logging.getLogger("mjegg.splash")


class Splash(QWidget):
    """显示约 1.2 秒后淡出并自动销毁; 无边框不抢焦点, 不进任务栏."""

    def __init__(self, app_icon):
        super().__init__(None,
                         Qt.WindowType.FramelessWindowHint
                         | Qt.WindowType.Tool
                         | Qt.WindowType.WindowStaysOnTopHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating)
        self.setFixedSize(300, 240)

        c = theme.palette_dict()
        v = QVBoxLayout(self)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(8)
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

        self._opacity = QGraphicsOpacityEffect(self)
        self._opacity.setOpacity(0.0)
        self.setGraphicsEffect(self._opacity)
        self._anim = QPropertyAnimation(self._opacity, b"opacity", self)
        self._anim.setDuration(450)

        self._place_center()

    def _place_center(self):
        screen = QGuiApplication.screenAt(QCursor.pos()) or QGuiApplication.primaryScreen()
        geo = screen.geometry()
        self.move(geo.center() - QPoint(self.width() // 2, self.height() // 2))

    def run(self):
        self.show()
        self._anim.stop()
        self._anim.setStartValue(0.0)
        self._anim.setEndValue(1.0)
        self._anim.start()
        log.info("splash: 屏幕中央显示图标 %s", self.geometry())
        QTimer.singleShot(2000, self._fade_out)
        return self

    def _fade_out(self):
        self._anim.stop()
        self._anim.setStartValue(self._opacity.opacity())
        self._anim.setEndValue(0.0)
        try:
            self._anim.finished.disconnect()
        except TypeError:
            pass
        self._anim.finished.connect(self.close)
        self._anim.start()
