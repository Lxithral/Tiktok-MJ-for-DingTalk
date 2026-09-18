# 项目长期约定（MJ-DingTalk）

## 交付流程（用户明确要求，必须遵守）

**每次编译完手机版 APK，都要发到微信「文件传输助手」**，方便用户在手机上取包。

- 用现成脚本：`app/desktop/poc/send_to_wechat.py <apk路径>`
- 原理：把文件放进剪贴板（`CF_HDROP`）→ 切到「文件传输助手」会话 → 输入框 `Ctrl+V`
  粘贴成附件 → 回车发送。走的是真实用户操作路径，不碰微信私有接口。
- 验证方式：截图看会话里出现 `<文件名> <大小>`，且不再显示「上传中」。
- 注意：`CF_HDROP` 相关的 Win32 句柄（`GlobalAlloc` / `GlobalLock` /
  `SetClipboardData`）在 64 位下**必须显式声明 argtypes/restype 为指针**，
  否则句柄被截断，粘贴会静默失败。

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
