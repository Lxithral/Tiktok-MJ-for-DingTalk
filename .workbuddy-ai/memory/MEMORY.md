# 项目长期约定（MJ-DingTalk）

## 交付流程（用户明确要求，必须遵守）

**每次编译完手机版 APK，都要发到微信「文件传输助手」**，方便用户在手机上取包。

- 用现成脚本：`app/desktop/poc/send_to_wechat.py <apk路径>`
- 发文字（例如 push 完的提醒）：`python send_to_wechat.py --text "已 push"`
- 原理：文件走剪贴板（`CF_HDROP`）+ `Ctrl+V` 粘贴成附件；文字直接按 Unicode 键输入。
  切到「文件传输助手」会话后回车发送。走真实用户操作路径，不碰微信私有接口。
- 验证方式：截图看会话里出现 `<文件名> <大小>`，且不再显示「上传中」。
- 注意：`CF_HDROP` 相关的 Win32 句柄（`GlobalAlloc` / `GlobalLock` /
  `SetClipboardData`）在 64 位下**必须显式声明 argtypes/restype 为指针**，
  否则句柄被截断，粘贴会静默失败。
- 微信窗口被最小化到托盘时脚本找不到主窗口，需要先让微信窗口可见。

**push 完也要发一条「已 push」的提醒**到同一个会话。

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
