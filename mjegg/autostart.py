# -*- coding: utf-8 -*-
"""开机自启动: HKCU 注册表 Run 键 (无需管理员权限).

打包后指向 exe 自身; 源码运行指向 pythonw + main.py (不弹终端).
"""
import logging
import os
import sys

import winreg

log = logging.getLogger("mjegg.autostart")

_RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
_VALUE_NAME = "MJDingTalk"


def _command() -> str:
    """写入 Run 键的启动命令."""
    if getattr(sys, "frozen", False):
        return f'"{sys.executable}"'
    pythonw = sys.executable.replace("python.exe", "pythonw.exe")
    if not os.path.exists(pythonw):
        pythonw = sys.executable
    script = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "main.py")
    return f'"{pythonw}" "{script}"'


def is_enabled() -> bool:
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_KEY) as k:
            winreg.QueryValueEx(k, _VALUE_NAME)
            return True
    except OSError:
        return False


def set_enabled(on: bool) -> bool:
    """设置开机自启, 返回操作后状态."""
    try:
        if on:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
                winreg.SetValueEx(k, _VALUE_NAME, 0, winreg.REG_SZ, _command())
        else:
            try:
                with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
                    winreg.DeleteValue(k, _VALUE_NAME)
            except FileNotFoundError:
                pass
        return is_enabled() == on
    except OSError as e:
        log.error("设置开机自启失败: %s", e)
        return False
