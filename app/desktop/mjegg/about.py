# -*- coding: utf-8 -*-
"""关于窗口: 应用图标 + 名称 + 版本徽章 + 支持平台徽片 + 开发者(真实头像) + GitHub + 致谢.

视觉规则: 红色只留给 GitHub 主按钮, 其余组件一律中性色;
中文文字不小于 12px(雅黑在更小字号下发虚); 深浅色跟随系统实时切换.
"""
import os

from PyQt6.QtCore import Qt, QRectF, QUrl
from PyQt6.QtGui import (QBrush, QColor, QDesktopServices, QFont, QGuiApplication, QIcon,
                         QPainter, QPainterPath, QPen, QPixmap)
from PyQt6.QtWidgets import QDialog, QHBoxLayout, QLabel, QPushButton, QVBoxLayout

from .paths import resource_path
from .overlay import draw_app_icon_pixmap
from . import theme

APP_NAME = "钉钉 MJ 彩蛋"
APP_VERSION = "1.4.0"
SUPPORTED_PLATFORMS = ("钉钉", "微信", "QQ", "抖音")
DEVELOPER = "L'xithral"
REPO_URL = "https://github.com/Lxithral/Tiktok-MJ-for-DingTalk"
CREDIT_URL = "https://github.com/qiu7c/Tiktok-MJ-for-Wechat"

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


def avatar_pixmap(source_path: str, size: int, border_color: str,
                  fallback_letter: str, fallback_bg: str, fallback_fg: str,
                  dpr: float = 1.0) -> QPixmap:
    """圆角方形头像(抗锯齿裁剪 + 描边). dpr>1 按物理像素渲染, 高 DPI 下不发虚.

    图片缺失时退回中性色首字母占位.
    """
    physical = int(size * dpr)
    pm = QPixmap(physical, physical)
    pm.fill(Qt.GlobalColor.transparent)
    p = QPainter(pm)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
    radius = physical * 0.30
    path = QPainterPath()
    path.addRoundedRect(QRectF(0, 0, physical, physical), radius, radius)
    src = QPixmap(source_path) if os.path.exists(source_path) else QPixmap()
    if not src.isNull():
        side = min(src.width(), src.height())
        cropped = src.copy((src.width() - side) // 2, (src.height() - side) // 2, side, side)
        p.setClipPath(path)
        p.drawPixmap(0, 0, physical, physical, cropped)
    else:
        p.fillPath(path, QBrush(QColor(fallback_bg)))
        font = QFont()
        font.setBold(True)
        font.setPixelSize(int(physical * 0.48))
        p.setFont(font)
        p.setPen(QColor(fallback_fg))
        p.drawText(QRectF(0, 0, physical, physical), Qt.AlignmentFlag.AlignCenter,
                   fallback_letter)
    p.setClipping(False)
    pen = QPen(QColor(border_color))
    pen.setWidthF(max(1.0, dpr))
    p.setPen(pen)
    p.drawPath(path)
    p.end()
    if dpr != 1.0:
        pm.setDevicePixelRatio(dpr)
    return pm


def _chip(text: str) -> QLabel:
    lab = QLabel(text)
    lab.setAlignment(Qt.AlignmentFlag.AlignCenter)
    lab.setFixedHeight(26)
    return lab


class AboutDialog(QDialog):
    def __init__(self, app_icon: QIcon, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"关于 {APP_NAME}")
        self.setFixedSize(380, 436)
        self.setWindowIcon(app_icon)
        self._icon = app_icon
        self._avatar_path = resource_path("assets/avatar.jpg")

        v = QVBoxLayout(self)
        v.setContentsMargins(28, 26, 28, 16)
        v.setSpacing(0)

        self._icon_label = QLabel()
        self._icon_label.setFixedSize(84, 84)
        self._icon_label.setScaledContents(True)
        self._icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        v.addWidget(self._icon_label, alignment=Qt.AlignmentFlag.AlignHCenter)
        v.addSpacing(12)

        self._name_label = QLabel(APP_NAME)
        self._name_label.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        v.addWidget(self._name_label)
        v.addSpacing(8)

        self._ver_chip = _chip(f"版本 {APP_VERSION}")
        v.addWidget(self._ver_chip, alignment=Qt.AlignmentFlag.AlignHCenter)
        v.addSpacing(16)

        self._platform_title = QLabel("支持平台")
        self._platform_title.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        v.addWidget(self._platform_title)
        v.addSpacing(8)

        chips = QHBoxLayout()
        chips.setSpacing(10)
        self._platform_chips = [_chip(p) for p in SUPPORTED_PLATFORMS]
        chips.addStretch(1)
        for chip in self._platform_chips:
            chips.addWidget(chip)
        chips.addStretch(1)
        v.addLayout(chips)
        v.addSpacing(16)

        self._line = QLabel()
        self._line.setFixedHeight(1)
        v.addWidget(self._line)
        v.addSpacing(14)

        row = QHBoxLayout()
        row.setSpacing(12)
        row.addStretch(1)
        self._avatar_label = QLabel()
        self._avatar_label.setFixedSize(52, 52)
        self._avatar_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
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
        self._gh_button.setFixedSize(132, 36)
        self._gh_button.clicked.connect(
            lambda: QDesktopServices.openUrl(QUrl(REPO_URL)))
        self._gh_button.setToolTip(REPO_URL)
        v.addWidget(self._gh_button, alignment=Qt.AlignmentFlag.AlignHCenter)
        v.addSpacing(10)

        self._credit = QLabel()
        self._credit.setOpenExternalLinks(True)
        self._credit.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        v.addWidget(self._credit)

        self.apply_theme()

    def _screen_dpr(self) -> float:
        """当前所在屏幕的 devicePixelRatio(未显示时退回主屏), 位图按它渲染才不虚."""
        screen = self.screen() or QGuiApplication.primaryScreen()
        return screen.devicePixelRatio() if screen else 1.0

    def showEvent(self, ev):
        # 弹出时按实际所在屏幕重渲染位图(多屏 DPR 不同/首帧前 DPR 未定)
        super().showEvent(ev)
        self.apply_theme()

    def apply_theme(self):
        c = theme.palette_dict()
        dpr = self._screen_dpr()
        self.setStyleSheet(f"QDialog {{ background: {c['window_bg']}; }}")
        self._icon_label.setPixmap(draw_app_icon_pixmap(84, dpr))

        chip_qss = (
            f"QLabel {{ background: {c['card_bg']}; color: {c['subtext']};"
            f" border: 1px solid {c['line']}; border-radius: 13px;"
            f" padding: 0 12px; font-size: 12px; font-weight: 500; }}")
        self._ver_chip.setStyleSheet(chip_qss)
        for chip in self._platform_chips:
            chip.setStyleSheet(chip_qss)

        for label, size, weight, color in (
            (self._name_label, 21, 700, c["text"]),
            (self._platform_title, 12, 400, c["subtext"]),
            (self._dev_title, 12, 400, c["subtext"]),
            (self._dev_name, 16, 600, c["text"]),
            (self._credit, 12, 400, c["subtext"]),
        ):
            label.setStyleSheet(
                f"color: {color}; font-size: {size}px; font-weight: {weight};"
                f" background: transparent;")
        # 链接颜色用内联 HTML(依赖主题), 不用 QSS 的 a 选择器 —— QLabel 富文本链接不走 QSS
        self._credit.setText(
            '动画素材与玩法致谢 <a href="%s" style="color:%s; text-decoration:none;">'
            'qiu7c/Tiktok-MJ-for-Wechat</a>' % (CREDIT_URL, c["accent"]))

        self._line.setStyleSheet(f"background: {c['line']}; border: none;")
        self._avatar_label.setPixmap(avatar_pixmap(
            self._avatar_path, 52, c["line"],
            DEVELOPER[0].upper(), c["card_bg"], c["subtext"], dpr))
        self._gh_button.setIcon(QIcon(github_icon_pixmap(18, c["window_bg"])))
        self._gh_button.setStyleSheet(
            f"QPushButton {{ background: {c['accent']}; color: {c['window_bg']};"
            f" border: none; border-radius: 18px; font-size: 14px; font-weight: 600; }}"
            f"QPushButton:hover {{ background: {c['hover']}; }}"
            f"QPushButton:pressed {{ background: {c['accent']}; }}")
