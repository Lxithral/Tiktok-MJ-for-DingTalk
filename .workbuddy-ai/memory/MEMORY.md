# 项目长期约定（MJ-DingTalk）

## 手机真机调试（ADB 无线）

adb 在 `D:/AAA_TOOLS/搞机工具箱10.1.0/adb.exe`（1.0.41 / 35.0.2）。
手机是 **Redmi K60（23013RK75C / mondrian），Android 17，HyperOS V816，arm64-v8a**。

**关键教训：用户给的 IP/端口经常是过期的。** 别照着用，直接 mDNS 发现：

```bash
ADB="D:/AAA_TOOLS/搞机工具箱10.1.0/adb.exe"
"$ADB" start-server
"$ADB" mdns services          # 列出 _adb-tls-pairing / _adb-tls-connect
"$ADB" pair <发现到的 IP:端口> <配对码>       # 配对端口每次开对话框都会变
"$ADB" connect <IP:连接端口>                  # 连接端口 ≠ 配对端口
```

连接端口若 mDNS 没广播，可以直接扫手机端口（实测落在 30000–50000）：
多线程 connect 扫一遍，能连上的就是 adb 连接端口。

**用 ADB 开无障碍服务**（省得手动点，也便于复现）：

```bash
"$ADB" shell settings put secure enabled_accessibility_services \
  com.lxithral.mjegg/com.lxithral.mjegg.egg.MjAccessibilityService
"$ADB" shell settings put secure accessibility_enabled 1
"$ADB" shell dumpsys accessibility | grep -A2 "Bound services"   # 确认真的绑上了
```

**微信触发链路的关键事实**（2026-09-25 真机抓包定位，详见 mobile README v1.0.7）：
- 微信的窗口树对无障碍**完全不可见**（rootInActiveWindow 只有根节点，uiautomator dump 也是空树），
  一切"轮询读节点"的方案在微信上都不可行；能用的只有**事件自带的 text 列表**（打字=["mj"]，清空=[]）
- 诊断广播（目标应用前台时发）：`"$ADB" shell am broadcast -a com.lxithral.mjegg.DUMP_TREE`，
  清日志 `-a com.lxithral.mjegg.CLEAR_LOG`；App 内「诊断」页有逐环结论
- uiautomator dump 对微信返回 407 字节空树属正常，别浪费时间重试

**钉钉触发链路的关键事实**（2026-09-26 真机抓包定位，详见 mobile README v1.0.8）：
- 钉钉清空输入框派发的光标事件，text 列表里装的是**占位 hint**（如 ["记录一下"]），
  不是空列表（微信）也不是用户文本 —— 直接喂状态机会把候选撤销掉。
  v1.0.8 修复：事件文本 == 节点 hintText ⇒ 按清空处理
- 钉钉窗口树对无障碍同样基本不可读；uiautomator dump 时灵时不灵（0 字节/407 字节/正常），
  不能依赖

**ADB 驱动手机自动化的坑**（2026-09-26 钉钉验证实战）：
- 输入框点击坐标依赖**键盘状态**：键盘收起时输入条在 y≈0.885H，键盘弹出时被顶到 y≈0.51H、
  发送键在 y≈0.56H 右端 —— 点错坐标会把 `input text` 打进键盘，全程空转。
  动手前先截屏看清键盘是否弹出
- 启动 App 用 `monkey -p <包名> -c android.intent.category.LAUNCHER 1`，
  别写死 Activity 组件名（钉钉手机版没有 .ui.LauncherUI，am start 直接报错静默失败）
- `uiautomator dump` 可能返回 0 字节/空树，重试 2 次为限；输出 407 字节=微信式空树
- 用户的无线 ADB 端口每次开关都变，掉线后扫 30000-50000 重连（配对端口也会出现在扫描结果里，
  connect 报 offline 的就是配对端口，跳过）
- 用户可能正通过**妙享桌面**从电脑投屏操作手机 —— 注入事件会和真人操作打架，
  动手前截屏确认当前界面，或直接让用户人肉验证

**Windows 侧的两个坑**：
- Git Bash 会把 `/sdcard/x.png` 这种路径转成 Windows 路径 → 必须 `export MSYS_NO_PATHCONV=1`
- Python 看不懂 Git Bash 的 `/tmp` → 截屏等临时文件写到
  `C:/Users/Lxithral/AppData/Local/Temp/mjshot/` 这种双方都认的路径
- 截屏用 `adb exec-out screencap -p > file.png`（不要用 shell screencap + pull，路径转换会踩坑）

## 交付流程（用户明确要求，必须遵守）

**手机版 APK 编译完直接 `adb install -r` 装到手机，不要发微信「文件传输助手」**。
（2026-09-25 用户指示，覆盖旧的"发文件传输助手 + push 后发提醒"流程；push 后也不用发提醒。）

- 设备经 ADB 连接（USB 或无线 mDNS 都行），装完可顺手 `am start` 拉起 App
- 桌面仍留一份备份：`D:\desktop\MJ彩蛋手机版-v<版本>.apk`
- release 签名，新版本能直接覆盖安装
- **HyperOS 坑**：重装/force-stop 后，只挂无障碍服务的进程很快会被 Greezer 冻结
  （广播报 `need cached broadcast`、事件停发）。装完先打开一次 App 再去聊天验证；
  已把 `com.lxithral.mjegg` 加进 `deviceidle whitelist`。ADB 广播（DUMP_TREE/CLEAR_LOG）
  被拦时，先拉起 App 让进程解冻再发。
- （旧流程曾用 `app/desktop/poc/send_to_wechat.py` 发微信，脚本留在仓库但不再是交付环节；
  里面的 Win32 剪贴板经验——CF_HDROP 句柄必须显式声明指针 argtypes、最小化窗口要用
  `GetWindowPlacement` 的 `rcNormalPosition` 才能还原——哪天再用得上就翻 git 历史。）

## git push 在这台机器上的坑（重要）

`git push` 会**挂死**，卡在凭据助手 `helper-selector` 上（trace 停在
`git config --system -e` 之后，6 分钟没动静）。`git ls-remote`（只读）反而正常，
所以很容易误判成"网络慢"。

原因：系统级 gitconfig 里 `credential.helper=helper-selector` 在无终端环境下会卡住。

**可用的 push 方式**（已验证，7.99MiB 秒传）：

```bash
GCM="C:/Users/Lxithral/.workbuddy-ai/binaries/PortableGit/versions/1.2.0/mingw64/bin/git-credential-manager.exe"
CRED=$(printf "protocol=https\nhost=github.com\n\n" | "$GCM" get)
USER=$(printf '%s\n' "$CRED" | sed -n 's/^username=//p')
PASS=$(printf '%s\n' "$CRED" | sed -n 's/^password=//p')
printf '%s:%s' "$USER" "$PASS" | base64 -w0 > /tmp/gh_b64.txt
git -c credential.helper= -c http.extraheader="Authorization: Basic $(cat /tmp/gh_b64.txt)" push origin main
rm -f /tmp/gh_b64.txt
```

要点：`-c credential.helper=`（空值）会清掉助手列表，从而绕开挂死的 selector；
直接调 `git-credential-manager.exe get` 能正常返回缓存凭据。

其他网络事实：
- 直连 github.com **不通**，必须走代理；全局 gitconfig 里 `http.proxy=http://127.0.0.1:7890`（Clash）
- 环境变量里的 `http_proxy=127.0.0.1:56730` 对 github **不通**，别用
- 7890 正常时 0.9s 能拿到 `info/refs`，偶尔会抖动到 16s（重试即可）

## 产物命名与位置

| 产物 | 位置 |
|---|---|
| 电脑版 exe | `app/desktop/dist/MJDingTalk.exe` |
| 电脑版绿色包 | `app/desktop/dist/MJDingTalk-green-v<版本>.zip` |
| 手机版 APK | 编译产物在 `app/mobile/app/build/outputs/apk/release/app-release.apk`，另复制一份到桌面 `D:\desktop\MJ彩蛋手机版-v<版本>.apk` |

APK 用 `~/.gradle/keystore.properties` 里的 key 做 release 签名（不用 debug 签名），
所以新版本能直接覆盖安装。

## 环境要点（这台机器）

- JDK 21：`C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot`（构建 Android 时必须设 `JAVA_HOME`）
- Android SDK：`C:/Users/Lxithral/AppData/Local/Android/Sdk`（compileSdk 37 / build-tools 37.0.0）
- 系统 Python（带 PyQt6/uiautomation/Pillow/pyinstaller）：
  `C:/Users/Lxithral/AppData/Local/Programs/Python/Python314/python.exe`
  —— 注意 PATH 上的 `python` 是另一个精简环境，**跑桌面版脚本要用上面这个全路径**
- 参考仓库都在 `D:\文档\GitHub`（miuix / KernelSU / Mishka / HyperLyric / ADBKit 等），
  其中 ADBKit 是可用的 Compose + miuix 工程模板
- Gradle transforms 缓存偶发「拒绝访问」（Windows 文件锁/杀软扫描），
  已在 `app/mobile/gradle.properties` 里把 `org.gradle.workers.max` 降到 2

## 代码约定

- 触发词规则两端必须一致：`app/desktop/mjegg/matcher.py` ↔
  `app/mobile/.../egg/Matcher.kt`，改一边要同步另一边和 README 的规则表
- 手机版核心判定逻辑（`EggTrigger`）保持"时钟可注入"，方便 JVM 单测
- 提交信息用中文，说明"改了什么 + 为什么"
