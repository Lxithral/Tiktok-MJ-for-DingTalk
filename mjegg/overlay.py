# -*- coding: utf-8 -*-
"""透明点击穿透全屏播放窗口 (PyQt6 + QMovie 播放带 alpha 的动画 WebP).

对应原项目的"独立穿透 UIWindow + AVPlayerLayer":
  - 两段动画内存索引交替播放
  - 播完自动销毁; 看门狗超时强制回收
  - WS_EX_TRANSPARENT 点击穿透, 不抢焦点不影响输入
"""
import logging
import os

from PyQt6.QtCore import Qt, QSize, QTimer, QPoint, QUrl, pyqtSignal, QObject
from PyQt6.QtGui import QGuiApplication, QMovie, QPainter, QColor, QFont
from PyQt6.QtMultimedia import QSoundEffect
from PyQt6.QtWidgets import QWidget, QLabel

from . import foreground
from .paths import resource_path

log = logging.getLogger("mjegg.overlay")

# (动画文件, 锚定边, 音效文件): 坠落→屏幕右上角, 荡绳→屏幕左上角
# 荡绳素材的蛛丝悬挂点在画面左边界之外, 必须贴左播放, 切断处才与屏幕边缘重合
CLIPS = [
    ("mj-drop-alpha.webp", "right", "mj-drop-alpha.wav"),
    ("mj-swing-alpha.webp", "left", "mj-swing-alpha.wav"),
]

# Win32 扩展样式
GWL_EXSTYLE = -20
WS_EX_LAYERED = 0x00080000
WS_EX_TRANSPARENT = 0x00000020
WS_EX_NOACTIVATE = 0x08000000
WS_EX_TOOLWINDOW = 0x00000080


def _make_click_through(hwnd_int: int):
    import ctypes
    user32 = ctypes.windll.user32
    style = user32.GetWindowLongPtrW(hwnd_int, GWL_EXSTYLE)
    user32.SetWindowLongPtrW(
        hwnd_int, GWL_EXSTYLE,
        style | WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW)


class EggWindow(QWidget):
    """一次播放 = 一个窗口实例, 播完即销毁. 用 spawn_egg_window() 创建."""

    closed = pyqtSignal()

    _active = set()  # 持有活跃窗口引用, 防止 Python 侧提前 GC

    def __init__(self, height_ratio=0.85, volume=1.0):
        super().__init__(None,
                         Qt.WindowType.FramelessWindowHint
                         | Qt.WindowType.WindowStaysOnTopHint
                         | Qt.WindowType.Tool)
        self._height_ratio = height_ratio
        self._volume = max(0.0, min(1.0, volume))
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating)
        self._label = QLabel(self)
        self._movie = None
        self._sound = None
        self._watchdog = QTimer(self)
        self._watchdog.setSingleShot(True)
        self._watchdog.timeout.connect(self._force_close)
        self.closed.connect(self.deleteLater)
        EggWindow._active.add(self)
        self.closed.connect(lambda w=None: EggWindow._active.discard(self))

    # ---------- 播放 ----------
    def play_on_foreground_screen(self):
        screen = self._pick_screen()
        geo = screen.geometry()
        self.setGeometry(geo)
        self.showFullScreen()

        idx = getattr(EggWindow, "_alt", 0)
        EggWindow._alt = (idx + 1) % len(CLIPS)  # 两段交替
        clip_path, anchor, sound_path = CLIPS[idx]
        path = resource_path(f"assets/{clip_path}")
        if not os.path.exists(path):
            log.error("素材缺失: %s", path)
            self._force_close()
            return

        probe_movie = QMovie(path)
        iw, ih = probe_movie.frameRect().width(), probe_movie.frameRect().height()
        if iw <= 0:
            iw, ih = 1072, 2352
        target_h = int(geo.height() * self._height_ratio)
        target_w = int(round(target_h * iw / ih))
        probe_movie.stop()
        del probe_movie

        self._movie = QMovie(path)
        self._movie.setScaledSize(QSize(target_w, target_h))
        x = (geo.width() - target_w) if anchor == "right" else 0  # 按素材锚定左/右上角
        self._label.setGeometry(x, 0, target_w, target_h)
        self._label.setMovie(self._movie)
        self._movie.frameChanged.connect(self._on_frame_changed)
        self._movie.start()

        # 音效与动画同步播放
        wav = resource_path(f"assets/{sound_path}")
        if os.path.exists(wav) and self._volume > 0:
            self._sound = QSoundEffect()
            self._sound.setSource(QUrl.fromLocalFile(wav))
            self._sound.setVolume(self._volume)
            self._sound.play()
        else:
            self._sound = None

        _make_click_through(int(self.winId()))
        self._watchdog.start(30 * 1000)
        log.info("开始播放 %s (%dx%d 锚定%s 声音=%s @ 屏幕 %s)",
                 clip_path, target_w, target_h, anchor, "开" if self._sound else "关", geo)

    def _pick_screen(self):
        _, _, rect = foreground.get_foreground_info()
        if rect:
            cx, cy = (rect[0] + rect[2]) // 2, (rect[1] + rect[3]) // 2
            sc = QGuiApplication.screenAt(QPoint(cx, cy))
            if sc:
                return sc
        return QGuiApplication.primaryScreen()

    def _on_frame_changed(self, frame_no):
        if self._movie and frame_no >= self._movie.frameCount() - 1:
            QTimer.singleShot(max(33, self._movie.nextFrameDelay()), self._force_close)

    def _force_close(self):
        if self._movie:
            self._movie.stop()
            self._movie = None
        if self._sound:
            self._sound.stop()
            self._sound = None
        self._watchdog.stop()
        self.close()
        self.closed.emit()

    def closeEvent(self, ev):
        if self._movie:
            self._movie.stop()
            self._movie = None
        if self._sound:
            self._sound.stop()
            self._sound = None
        super().closeEvent(ev)


def spawn_egg_window(height_ratio=0.85, volume=1.0) -> EggWindow:
    """每次播放新建窗口实例(旧窗口播完自动销毁), 避免复用已删除对象."""
    return EggWindow(height_ratio, volume)


def draw_tray_icon_pixmap(size=64):
    """生成一个红底白字 MJ 图标(无需外部 .ico)."""
    from PyQt6.QtGui import QPixmap, QIcon
    pm = QPixmap(size, size)
    pm.fill(Qt.GlobalColor.transparent)
    p = QPainter(pm)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setBrush(QColor(230, 60, 60))
    p.setPen(Qt.PenStyle.NoPen)
    p.drawRoundedRect(2, 2, size - 4, size - 4, 14, 14)
    p.setPen(QColor(255, 255, 255))
    f = QFont()
    f.setBold(True)
    f.setPixelSize(int(size * 0.42))
    p.setFont(f)
    p.drawText(pm.rect(), Qt.AlignmentFlag.AlignCenter, "MJ")
    p.end()
    return QIcon(pm)
