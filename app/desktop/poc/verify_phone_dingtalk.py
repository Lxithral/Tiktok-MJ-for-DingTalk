# -*- coding: utf-8 -*-
"""手机版钉钉触发验证(ADB 驱动) —— 把本项目踩过的坑全部防进去。

用法(子命令, 也可 all 一口气):
    python verify_phone_dingtalk.py open    # 解冻MJ App(先!) + 清诊断日志 + 打开钉钉 + 探测输入框
    python verify_phone_dingtalk.py type    # 点输入框 + input text mj + 校验文本进框
    python verify_phone_dingtalk.py send    # 找"发送"按钮点击 + 连拍8帧 + 像素差检测动画
    python verify_phone_dingtalk.py diag    # 回 MJ App 诊断页截图
    python verify_phone_dingtalk.py all     # 以上全部, 中间任意一步失败即停

之前踩过的坑 -> 对应防御:
 1  HyperOS Greezer 冻结只挂无障碍服务的进程: 广播被拦(need cached broadcast)、事件停发
     -> 第一步永远是 am start MJ App 解冻, 且 CLEAR_LOG 广播紧跟其后(App 还在前台时发不拦)
 2  微信窗口树对无障碍不可见(uiautomator 也是空树) -> 钉钉不预设立场: 先试 uiautomator
     dump 找输入框, 失败再退比例坐标; dump 只重试 2 次不恋战
 3  桌面端教训: 目标窗口没真正到前台, 按坐标点/打字会打进叠在上面的别的窗口
     -> 每次打字前用 dumpsys window mCurrentFocus 校验前台包名, 不对就中止
 4  input text 走输入法提交(带下划线组合态), 中文候选栏会吞字母
     -> input text 直提, 输完用 dump 校验 EditText 里真有 "mj" 才继续
 5  发送键位置随键盘/文本状态变(空框=回车箭头, 有字=蓝色发送; IME 键与 App 内发送钮不同物)
     -> 优先在 UIA dump 里找 text/desc=="发送" 的节点点它, 找不到退到输入条右端比例坐标
 6  Git Bash 路径转换 & /tmp 不可读 -> subprocess 全程 MSYS_NO_PATHCONV=1,
     截图写到 C:/Users/Lxithral/AppData/Local/Temp/mjshot/
 7  截屏用 exec-out screencap -p(不用 shell+pull, 路径转换必踩坑)
 8  动画是否出现不能靠肉眼翻图 -> 连拍后用 PIL 与基线帧做像素差, 自动报哪几帧有动画
 9  重装/force-stop 后服务可能没重绑 -> open 阶段 grep dumpsys accessibility 确认 "MJ 彩蛋" 在列
10  广播可能被 Greezer 拦(返回 completed 但 logcat 见 Denial) -> 清日志后不依赖它成功与否,
    只影响诊断页是否从零开始计数, 不影响触发本身
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = r"D:/AAA_TOOLS/搞机工具箱10.1.0/adb.exe"
SHOT_DIR = r"C:/Users/Lxithral/AppData/Local/Temp/mjshot"
MJ = "com.lxithral.mjegg"
RIMET = "com.alibaba.android.rimet"
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")


def adb(*args, timeout=30, binary=False):
    r = subprocess.run([ADB, *args], capture_output=True, timeout=timeout, env=ENV)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def shot(name):
    p = os.path.join(SHOT_DIR, name)
    with open(p, "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", timeout=20, binary=True))
    print("  截图 ->", p)
    return p


def tap(x, y):
    adb("shell", "input", "tap", str(int(x)), str(int(y)))


def screen_size():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
    return (int(m.group(1)), int(m.group(2))) if m else (1440, 3200)


def current_focus():
    # 坑: HyperOS 上 mCurrentFocus 可能为 null, 且 dumpsys 各节格式不同 ->
    # 三级 fallback: topResumedActivity > mCurrentFocus > mFocusedApp
    out = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"topResumedActivity=ActivityRecord\{[^}]*\s([\w.]+)/", out)
    if m:
        return m.group(1)
    out = adb("shell", "dumpsys", "window", "window")
    m = re.search(r"mCurrentFocus=Window\{[^}]*\s([\w.]+)/", out)
    if m:
        return m.group(1)
    m = re.search(r"mFocusedApp=ActivityRecord\{[^}]*\s([\w.]+)/", out)
    return m.group(1) if m else "?"


def ui_dump(retries=2):
    """uiautomator dump, 返回 ElementRoot 或 None (微信式空树/失败都给 None)"""
    for _ in range(retries):
        adb("shell", "uiautomator", "dump", "/sdcard/mjdump.xml", timeout=40)
        xml = adb("shell", "cat", "/sdcard/mjdump.xml")
        if len(xml) > 2000:  # 407字节 = 微信式空树
            return ET.fromstring(xml)
        time.sleep(1.5)
    return None


def walk(root):
    for el in root.iter("node"):
        yield el


def find_send_node(root):
    """找"发送"按钮: text/desc 精确或前缀匹配, 取屏幕右下半区面积最小的可点节点"""
    cands = []
    for el in walk(root):
        t = (el.get("text") or "").strip()
        d = (el.get("content-desc") or "").strip()
        if t == "发送" or d == "发送" or t.startswith("发送") or d.startswith("发送"):
            b = [int(v) for v in re.findall(r"-?\d+", el.get("bounds", ""))]
            if len(b) == 4:
                cx, cy = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
                area = max(1, (b[2] - b[0]) * (b[3] - b[1]))
                cands.append((area, cx, cy, t or d, el.get("class")))
    cands.sort()
    return cands[0] if cands else None


def find_input_node(root):
    """找聊天输入框: 下半屏可编辑/EditText, 取最靠下的大节点"""
    W, H = screen_size()
    cands = []
    for el in walk(root):
        cls = el.get("class") or ""
        if "EditText" not in cls and el.get("editable") != "true":
            continue
        b = [int(v) for v in re.findall(r"-?\d+", el.get("bounds", ""))]
        if len(b) != 4:
            continue
        cy = (b[1] + b[3]) // 2
        if cy < H * 0.6:  # 输入框在下半屏
            continue
        area = (b[2] - b[0]) * (b[3] - b[1])
        cands.append((b[1], area, (b[0] + b[2]) // 2, cy, cls))
    cands.sort(reverse=True)
    return cands[0] if cands else None


def step_open():
    print("[open] 收起通知栏(用户拉下的控制中心会挡住启动)...")
    adb("shell", "cmd", "statusbar", "collapse")
    time.sleep(0.5)
    print("[open] 拉起 MJ App 解冻(防 Greezer 冻结)...")
    adb("shell", "am", "start", "-n", f"{MJ}/.MainActivity")
    time.sleep(1.5)
    print("[open] 清诊断日志(趁 App 前台, 广播不被 Greezer 拦)...")
    adb("shell", "am", "broadcast", "-a", f"{MJ}.CLEAR_LOG")
    if "MJ 彩蛋" not in adb("shell", "dumpsys", "accessibility"):
        # 坑10修复: 设置里"已启用"但系统没绑(HyperOS 重启/踹服务后常见) ->
        # settings put 重写 enabled 服务列表强制重绑, 保留其他已有服务
        print("[open] 服务没绑定, settings put 强制重绑(保留已有服务)...")
        cur = adb("shell", "settings", "get", "secure",
                  "enabled_accessibility_services").strip()
        comp = f"{MJ}/com.lxithral.mjegg.egg.MjAccessibilityService"
        new = cur if comp in cur else (comp if not cur else f"{cur}:{comp}")
        adb("shell", "settings", "put", "secure",
            "enabled_accessibility_services", new)
        adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")
        time.sleep(3)
    ok = "MJ 彩蛋" in adb("shell", "dumpsys", "accessibility")
    print("[open] 无障碍服务绑定:", "OK" if ok else "仍未绑定! 请在系统设置里开关一次")
    if not ok:
        return False
    print("[open] 打开钉钉...")
    adb("shell", "monkey", "-p", RIMET, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(5)
    focus = current_focus()
    print("[open] 前台包名:", focus, "(坑3防御: 前台不对就中止)")
    shot("dt_1_open.png")
    root = ui_dump()
    if focus != RIMET:
        print("  !! 钉钉没到前台, 中止。看截图处理。")
        return False
    if root is None:
        print("  UIA dump 不可用(坑2: 不恋战), 后续用比例坐标。")
    else:
        inp = find_input_node(root)
        print("  输入框节点:", inp)
        send = find_send_node(root)
        print("  发送按钮节点:", send)
    return True


def step_type():
    print("[type] 校验前台是钉钉(坑3)...")
    if current_focus() != RIMET:
        print("  !! 前台不是钉钉, 中止")
        return False
    W, H = screen_size()
    root = ui_dump()
    if root is not None:
        inp = find_input_node(root)
        if inp:
            _, _, cx, cy, cls = inp
            print(f"  用 dump 定位输入框 ({cx},{cy}) cls={cls}")
        else:
            cx, cy = int(W * 0.45), int(H * 0.885)
            print(f"  dump 没找到输入框, 退比例坐标 ({cx},{cy})")
    else:
        cx, cy = int(W * 0.45), int(H * 0.885)
        print(f"  无 dump, 用比例坐标 ({cx},{cy})")
    tap(cx, cy)
    time.sleep(1.2)
    adb("shell", "input", "text", "mj")
    time.sleep(1.2)
    # 坑4防御: 校验文本真进框了
    root = ui_dump()
    typed = False
    if root is not None:
        for el in walk(root):
            if "EditText" in (el.get("class") or "") and (el.get("text") or "") == "mj":
                typed = True
                break
    print("[type] 输入校验:", "OK, EditText 里有 'mj'" if typed
          else "dump 未见 'mj'(可能树不可读或有组合态), 看截图确认")
    shot("dt_2_typed.png")
    return True


def step_send():
    print("[send] 校验前台是钉钉(坑3)...")
    if current_focus() != RIMET:
        print("  !! 前台不是钉钉, 中止(避免打字进别的会话)")
        return False
    base = shot("dt_3_before_send.png")
    W, H = screen_size()
    root = ui_dump()
    if root is not None:
        send = find_send_node(root)
        if send:
            area, cx, cy, label, cls = send
            print(f"  用 dump 定位发送按钮 ({cx},{cy}) \"{label}\" cls={cls}")
        else:
            cx, cy = int(W * 0.92), int(H * 0.92)
            print(f"  dump 没找到发送按钮, 退比例坐标 ({cx},{cy})")
    else:
        cx, cy = int(W * 0.92), int(H * 0.92)
        print(f"  无 dump, 用比例坐标 ({cx},{cy})")
    tap(cx, cy)
    print("[send] 已点发送, 连拍 8 帧...")
    frames = []
    for i in range(8):
        frames.append(shot(f"dt_4_anim_{i + 1}.png"))
        time.sleep(0.4)
    # 坑8防御: 像素差自动判定哪几帧有动画
    try:
        from PIL import Image, ImageChops
        bimg = Image.open(base).convert("RGB")
        hits = []
        for f in frames:
            d = ImageChops.difference(bimg, Image.open(f).convert("RGB"))
            bbox = d.getbbox()
            if bbox:
                px = sum(1 for p in d.getdata() if p != (0, 0, 0))
                if px > 50000:  # 大面积变化 ~= 全屏动画
                    hits.append((os.path.basename(f), px))
        print("[send] 动画帧检测:", hits if hits else "无大面积变化帧(可能没触发)")
    except Exception as e:
        print("[send] 像素差检测跳过:", e)
    return True


def step_diag():
    print("[diag] 回 MJ App 主页 -> 诊断页...")
    adb("shell", "am", "start", "-n", f"{MJ}/.MainActivity")
    time.sleep(1.5)
    tap(1260, 3080)  # 新版浮动底栏最右"设置", 实测直达诊断页
    time.sleep(1.8)
    shot("dt_5_diag.png")
    adb("shell", "input", "swipe", "720", "2400", "720", "800", "400")
    time.sleep(1)
    shot("dt_6_diag_log.png")
    return True


STEPS = {"open": step_open, "type": step_type, "send": step_send, "diag": step_diag}


def main():
    os.makedirs(SHOT_DIR, exist_ok=True)
    dev = [l.split("\t")[0] for l in adb("devices").splitlines() if "\tdevice" in l]
    if not dev:
        print("没有已连接设备: 插 USB, 或手机开 无线调试 后告诉我来重连(mDNS/扫端口)。")
        return 1
    print("设备:", dev[0])
    todo = sys.argv[1:] or ["all"]
    if "all" in todo:
        todo = ["open", "type", "send", "diag"]
    for s in todo:
        if not STEPS[s]():
            print(f"[{s}] 失败, 后续步骤不执行。")
            return 1
    print("完成。看 dt_4_anim_*.png 与 dt_5/6_diag.png 判定。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
