# -*- coding: utf-8 -*-
"""连续播放 6 次彩蛋动画(约 23 秒)供外部截图验证; 每次播放 1 秒后保存 win.grab() 渲染图."""
import os
import sys
sys.path.insert(0, "..")

from PyQt6.QtCore import QTimer
from PyQt6.QtWidgets import QApplication

from mjegg.overlay import spawn_egg_window


def main():
    app = QApplication(sys.argv)
    out_dir = os.path.dirname(os.path.abspath(__file__))
    state = {"n": 0, "closed": 0}

    def play_once():
        state["n"] += 1
        n = state["n"]
        win = spawn_egg_window(height_ratio=0.85)
        orig = win._force_close

        def tracked():
            orig()
            state["closed"] += 1

        win._force_close = tracked
        win.closed.connect(lambda: print(f"play#{n} closed", flush=True))
        win.play_on_foreground_screen()
        print(f"play#{n} started", flush=True)

        def snap():
            try:
                if win.isVisible():
                    pm = win.grab()
                    path = os.path.join(out_dir, f"qt_render_{n}.png")
                    pm.save(path)
                    print("saved", path, flush=True)
            except RuntimeError:
                pass

        QTimer.singleShot(1000, snap)

    play_once()
    t = QTimer()
    t.timeout.connect(play_once)
    t.start(3800)
    QTimer.singleShot(23500, app.quit)
    app.exec()
    print("播放:", state["n"], "自动关闭:", state["closed"], flush=True)


if __name__ == "__main__":
    main()
