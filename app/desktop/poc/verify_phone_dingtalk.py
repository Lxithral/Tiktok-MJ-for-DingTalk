# -*- coding: utf-8 -*-
"""手机版钉钉触发验证(ADB 驱动, 手机连上后一条命令跑完).

用法:
    python verify_phone_dingtalk.py

前置: 手机已开 USB 调试或无线调试并连上 ADB(脚本会先探测, 没连上会提示).

流程: 拉起 MJ App 解冻(防 HyperOS 冻结) -> 打开钉钉 -> 截图确认 ->
     点输入框输入 mj -> 截图 -> 点发送 -> 连拍 8 帧抓动画 ->
     回 MJ App 诊断页截图(看"钉钉"一环的结论).

注意: 会真的往钉钉会话里发一条 "mj"(默认发到打开的会话, 建议用文件小助手/自己的会话).
"""
import os
import subprocess
import sys
import time

ADB = r"D:/AAA_TOOLS/搞机工具箱10.1.0/adb.exe"
SHOT_DIR = r"C:/Users/Lxithral/AppData/Local/Temp/mjshot"

export = dict(os.environ, MSYS_NO_PATHCONV="1")


def adb(*args, timeout=30):
    return subprocess.run([ADB, *args], capture_output=True, timeout=timeout,
                          env=export).stdout


def shot(name):
    p = os.path.join(SHOT_DIR, name)
    with open(p, "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", timeout=20))
    print("截图:", p)
    return p


def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))


def wait(sec):
    time.sleep(sec)


def device_ready():
    out = adb("devices").decode("utf-8", "replace")
    lines = [l for l in out.splitlines() if "\tdevice" in l]
    return lines[0].split("\t")[0] if lines else None


def main():
    os.makedirs(SHOT_DIR, exist_ok=True)
    dev = device_ready()
    if not dev:
        print("没有已连接的设备。请任选其一:")
        print("  1. 插上 USB 线(手机已开 USB 调试)")
        print("  2. 手机打开 无线调试, 然后告诉我, 我用 mDNS/扫端口重连")
        return 1
    print("设备:", dev)

    # 1. 拉起 MJ App(解冻进程, HyperOS 会冻结只挂无障碍服务的后台进程)
    adb("shell", "am", "start", "-n", "com.lxithral.mjegg/.MainActivity")
    wait(1.5)
    # 2. 打开钉钉
    adb("shell", "monkey", "-p", "com.alibaba.android.rimet",
        "-c", "android.intent.category.LAUNCHER", "1")
    wait(5)
    shot("dt_verify_1_open.png")

    # 3~6 需要看第一张截图人工定位(钉钉界面版式因版本/会话而异),
    # 输入框/发送按钮坐标由调用者按截图给, 也支持 --auto 用默认估计值。
    if "--auto" in sys.argv:
        size = adb("shell", "wm", "size").decode()
        w = int(size.split(":")[1].split("x")[0])
        h = int(size.split(":")[1].split("x")[1].strip())
        # 钉钉聊天页: 输入框在底部 (~92% 高度), 发送按钮出现在右下角
        tap(int(w * 0.45), int(h * 0.92))
        wait(1.2)
        adb("shell", "input", "text", "mj")
        wait(1.2)
        shot("dt_verify_2_typed.png")
        tap(int(w * 0.92), int(h * 0.92))
        for i in range(8):
            shot(f"dt_verify_3_anim_{i + 1}.png")
            wait(0.45)
        # 回诊断页看结论
        adb("shell", "am", "start", "-n", "com.lxithral.mjegg/.MainActivity")
        wait(1.5)
        # 设置 -> 诊断 (新版浮动底栏的坐标在真机上核对过: (1260,3080) -> 设置)
        tap(int(1260), int(3080))
        wait(1.8)
        shot("dt_verify_4_diag.png")
    else:
        print("第一张截图已保存; 告诉我输入框/发送按钮位置, 我继续跑或直接用 --auto 重跑。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
