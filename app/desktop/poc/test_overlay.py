# -*- coding: utf-8 -*-
"""播放窗口渲染验证: 播放彩蛋动画, 期间抓屏存图目检 (alpha 合成是否正确)."""
import os
import sys
sys.path.insert(0, "..")

from PIL import ImageGrab
from PyQt6.QtCore import QTimer
from PyQt6.QtWidgets import QApplication

from mjegg.overlay import EggWindow


def main():
    app = QApplication(sys.argv)
    win = EggWindow(height_ratio=0.85)
    win.play_on_foreground_screen()

    shots = []
    capture_times = [800, 1800, 2800]  # ms

    def grab():
        img = ImageGrab.grab()
        path = os.path.join(os.path.dirname(__file__), f"overlay_shot_{len(shots)}.png")
        img.save(path)
        shots.append(path)
        print("saved", path)

    for t in capture_times:
        QTimer.singleShot(t, grab)
    QTimer.singleShot(4200, app.quit)  # 动画 3 秒, 留时间看是否自动关闭
    QTimer.singleShot(4200, lambda: print("窗口存活状态(应False):", win.isVisible()))
    app.exec()
    print("结束, 共截图", len(shots))


if __name__ == "__main__":
    main()
