# -*- coding: utf-8 -*-
"""关于窗口: 应用图标 + 名称 + 版本号 + 开发者(圆形头像) + GitHub 按钮 + 致谢, 深浅色跟随系统."""
import os

from PyQt6.QtCore import Qt, QRectF, QUrl
from PyQt6.QtGui import QDesktopServices, QIcon, QPainter, QPainterPath, QPixmap
from PyQt6.QtWidgets import QDialog, QHBoxLayout, QLabel, QPushButton, QVBoxLayout

from .paths import resource_path
from . import theme

APP_NAME = "钉钉 MJ 彩蛋"
APP_VERSION = "1.2.0"
DEVELOPER = "L'xithral"
REPO_URL = "https://github.com/Lxithral/Tiktok-MJ-for-DingTalk"

# GitHub 官方 octocat 图标 (mark-github, MIT License, (c) GitHub)
GITHUB_MARK_SVG = (
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="{color}">'
    '<path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 '
    '0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 '
    '1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 '
    '0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 '
    '2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 '
    '0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>'
)


def github_icon_pixmap(size: int, color: str) -> QPixmap:
    from PyQt6.QtSvg import QSvgRenderer
    renderer = QSvgRenderer(bytes(GITHUB_MARK_SVG.format(color=color), encoding="utf-8"))
    pm = QPixmap(size, size)
    pm.fill(Qt.GlobalColor.transparent)
    p = QPainter(pm)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    renderer.render(p)
    p.end()
    return pm


def circle_pixmap(source_path: str, diameter: int) -> QPixmap:
    """把方形图片裁成抗锯齿圆形头像."""
    src = QPixmap(source_path)
    result = QPixmap(diameter, diameter)
    result.fill(Qt.GlobalColor.transparent)
    p = QPainter(result)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
    path_ = QPainterPath()
    path_.addEllipse(QRectF(0, 0, diameter, diameter))
    p.setClipPath(path_)
    side = min(src.width(), src.height())
    src = src.copy((src.width() - side) // 2, (src.height() - side) // 2, side, side)
    p.drawPixmap(0, 0, diameter, diameter, src)
    p.end()
    return result


class AboutDialog(QDialog):
    def __init__(self, app_icon: QIcon, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"关于 {APP_NAME}")
        self.setFixedSize(360, 420)
        self.setWindowIcon(app_icon)

        self._icon = app_icon
        avatar_path = resource_path("assets/avatar.jpg")
        self._avatar_pm = (circle_pixmap(avatar_path, 96)
                           if os.path.exists(avatar_path) else QPixmap())

        v = QVBoxLayout(self)
        v.setContentsMargins(28, 26, 28, 20)
        v.setSpacing(6)

        self._icon_label = QLabel()
        self._icon_label.setFixedSize(76, 76)
        self._icon_label.setScaledContents(True)
        self._icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        v.addWidget(self._icon_label, alignment=Qt.AlignmentFlag.AlignHCenter)

        self._name_label = QLabel(APP_NAME)
        self._name_label.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        self._ver_label = QLabel(f"版本 {APP_VERSION}")
        self._ver_label.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        v.addWidget(self._name_label)
        v.addWidget(self._ver_label)
        v.addSpacing(10)

        line = QLabel()
        line.setFixedHeight(1)
        v.addWidget(line)
        self._line = line
        v.addSpacing(8)

        row = QHBoxLayout()
        row.setSpacing(14)
        self._avatar_label = QLabel()
        self._avatar_label.setFixedSize(64, 64)
        if not self._avatar_pm.isNull():
            self._avatar_label.setPixmap(self._avatar_pm.scaled(
                64, 64, Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation))
        row.addStretch(1)
        row.addWidget(self._avatar_label)
        col = QVBoxLayout()
        col.setSpacing(2)
        self._dev_title = QLabel("开发者")
        self._dev_name = QLabel(DEVELOPER)
        col.addWidget(self._dev_title)
        col.addWidget(self._dev_name)
        row.addLayout(col)
        row.addStretch(1)
        v.addLayout(row)
        v.addStretch(1)

        self._gh_button = QPushButton(" GitHub")
        self._gh_button.setCursor(Qt.CursorShape.PointingHandCursor)
        self._gh_button.setFixedSize(110, 34)
        self._gh_button.clicked.connect(
            lambda: QDesktopServices.openUrl(QUrl(REPO_URL)))
        self._gh_button.setToolTip(REPO_URL)
        v.addWidget(self._gh_button, alignment=Qt.AlignmentFlag.AlignHCenter)
        v.addSpacing(4)

        self._credit = QLabel('动画素材与玩法致谢 qiu7c/Tiktok-MJ-for-Wechat')
        self._credit.setOpenExternalLinks(True)
        self._credit.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        v.addWidget(self._credit)

        self.apply_theme()

    def apply_theme(self):
        c = theme.palette_dict()
        self.setStyleSheet(f"QDialog {{ background: {c['window_bg']}; }}")
        self._icon_label.setPixmap(self._icon.pixmap(72, 72))
        for label, size, weight, color in (
            (self._name_label, 19, 700, c["text"]),
            (self._ver_label, 12, 400, c["subtext"]),
            (self._dev_title, 11, 400, c["subtext"]),
            (self._dev_name, 16, 600, c["text"]),
            (self._credit, 10, 400, c["subtext"]),
        ):
            label.setStyleSheet(
                f"color: {color}; font-size: {size}px; font-weight: {weight}; background: transparent;")
        self._line.setStyleSheet(f"background: {c['line']}; border: none;")
        self._gh_button.setIcon(QIcon(github_icon_pixmap(18, c["window_bg"])))
        self._gh_button.setStyleSheet(
            f"QPushButton {{ background: {c['accent']}; color: {c['window_bg']};"
            f" border: none; border-radius: 17px; font-size: 14px; font-weight: 600; }}"
            f"QPushButton:hover {{ background: {c['hover']}; }}"
            f"QPushButton:pressed {{ background: {c['accent']}; }}")
