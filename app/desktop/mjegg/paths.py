# -*- coding: utf-8 -*-
"""资源与应用目录适配: 兼容源码运行和 PyInstaller 打包(onfile)两种形态.

- 打包后: 只读资源(素材/头像/图标)从解包临时目录 sys._MEIPASS 读;
  可写文件(config.json / 日志)放在 exe 旁边, 方便用户改配置.
- 源码运行: 一律用项目根目录.
"""
import os
import sys


def is_frozen() -> bool:
    return getattr(sys, "frozen", False)


def bundle_root() -> str:
    """只读资源所在根目录(打包后为解包临时目录)."""
    if is_frozen():
        return sys._MEIPASS
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def app_dir() -> str:
    """可写文件(config.json/日志)所在目录: 打包后为 exe 所在目录."""
    if is_frozen():
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def resource_path(rel: str) -> str:
    """按包内相对路径取只读资源."""
    return os.path.join(bundle_root(), rel.replace("/", os.sep))
