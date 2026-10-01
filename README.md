# MJ 彩蛋 · 多端（微信 / QQ / 钉钉 / 抖音）

在聊天窗口里**自己发送** `mj`、`mjmj`、`MJ`、`MjMj` 等组合时，全屏播放一段带透明通道的蜘蛛侠动画，播完自动消失——不抢焦点、不影响打字、不注入、不自动发消息。

玩法与动画素材源自 iOS 越狱插件 [qiu7c/Tiktok-MJ-for-Wechat](https://github.com/qiu7c/Tiktok-MJ-for-Wechat)（抖音 MJ 蜘蛛侠彩蛋）。

本仓库包含两端实现：

| 端 | 技术栈 | 位置 |
|---|---|---|
| **手机版**（Android） | Kotlin + Jetpack Compose + miuix | `app/mobile/` |
| **电脑版**（Windows） | Python + PyQt6 | `app/desktop/` |

两端共用同一套触发词规则与同一份动画素材。

---

## 手机版 v2.0（Android）

### 安装

从 [Releases](../../releases) 下载最新的 `MJ彩蛋手机版-v2.0.apk`（约 11MB），直接安装即可（release 签名，后续版本可直接覆盖升级）。支持 **Android 11 ~ 17**（minSdk 30）。

### 首次使用

1. 打开 App，进入首启引导（OOBE）。
2. **权限设置**页按提示跳转系统「无障碍 → 已下载的应用 / 已安装的服务」，打开 **MJ 彩蛋**的开关；不着急用也可以点「暂不设置」跳过，之后再开。
3. **保活设置**页建议按提示完成三项：允许自启动、忽略电池优化（省电策略=无限制）、最近任务里下拉锁定后台——不然息屏久了系统会冻结服务，收不到 `mj`。
4. **基础设置**页勾选要监控的聊天应用。
5. 完成后在微信 / QQ / 钉钉 / 抖音的聊天输入框里发送 `mj`，动画即播。

App 主页的状态卡实时显示服务状态；不触发时去 **设置 → 诊断** 看逐环结论（能直接区分"事件没进来"、"文本读不到"、"命中过但没观察到发送"）。

### 主要特性

- **全屏彩蛋动画**：坠落（右上角锚定）/ 荡绳（左上角锚定）两段自动交替，带原版音效，播完自动消失
- **HyperOS 风格 UI**：miuix 组件库、深浅色四态（跟随系统/浅色/深色/AMOLED）、Monet 动态取色 + 15 色主题色预设
- **三形态底栏**：标准（miuix NavigationBar）/ 悬浮胶囊 / 液态玻璃（Android 13+），三大金刚键与手势导航都已适配
- **顶栏 progressive 渐变模糊**、状态栏/导航栏深浅自动跟随页面
- **全机型统一 MiSans 字体**（正文 Medium 字重，静态子集打包，仅 +0.3MB）
- **开发者模式**：关于页连点版本号 7 次解锁（诊断、重跑首启引导）
- **隐私**：全程只读——无障碍服务只读取聊天输入框文本变化，不注入按键、不模拟点击、不自动发送任何消息

### 触发规则（与电脑版一致）

长度为偶数且每 2 个字符一组均为 `mj`（不区分大小写）：

| 输入 | 结果 |
|------|------|
| `mj` / `MJ` / `Mj` | ✅ 触发 |
| `mjmj` / `MJMJ` / `MjMj` | ✅ 触发 |
| `mjm` / `mjx` / `ajmj` / `amj` | ❌ 不触发 |
| 中文 / 其他文字 | ❌ 不触发 |

只响应**自己发送**的消息：对方发的 `mj` 不会触发。中文输入法下把字母"上屏"进输入框（未发送）不会触发，真正发送出去时才触发。

### 构建

```bash
cd app/mobile
./gradlew :app:assembleRelease      # 产出 app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest    # 19 个 JVM 单测(触发词规则 + 触发状态机)
```

- 需要 JDK 21 与 Android SDK（`local.properties` 的 `sdk.dir`）。
- release 签名读 `~/.gradle/keystore.properties`，文件不存在时自动回退 debug 签名。
- 已开启 **R8 混淆 + 资源收缩**（APK 38.4MB → 11.2MB）；无障碍服务与入口组件在 `proguard-rules.pro` 里 keep。
- 字体：`fonts-src/make_misans.py` 从 MiSans VF 离线实例化 500/700 字重并按界面文案子集化（产物 `res/font/misans_500|700.ttf` 各约 140KB；界面新增文案出现"豆腐块"时重跑一次）。

### 真机验证

微信 / QQ / 钉钉 / 抖音四端均已真机实测触发（Redmi K60·Android 17、Redmi Note 8 Pro·Android 11、Redmi Note 11 5G·Android 13）。

---

## 电脑版（Windows）

**快速开始**：双击 `app/desktop/dist/MJDingTalk.exe`，或 `app/desktop/启动彩蛋.bat`。

启动后驻留系统托盘（红底 MJ 图标），右键菜单：**启用/停用彩蛋、播放测试、开机自启、关于、退出**。动画点击穿透、不抢焦点，原版音效同步播放，两段动画自动交替并按素材构图锚定角落（坠落=右上、荡绳=左上）。

**源码运行**：

```bat
cd app/desktop
pip install PyQt6 uiautomation pywin32 Pillow
python main.py
```

**打包**：双击 `app/desktop/build_exe.bat`。

### 各客户端输入框适配（UIA 实测）

| 客户端 | 进程 | 输入框定位 | 读文本 | 需要唤醒 |
|---|---|---|---|---|
| 钉钉 | `DingTalk.exe` | 类名全等 `im_chat::InputRichTextEdit` | ValuePattern | 否 |
| 微信 4.x | `Weixin.exe` | 类名全等 `mmui::ChatInputField` | ValuePattern | 否 |
| QQ NT | `QQ.exe` | 类名子串 `ExEditor-qq-msg-editor` | 遍历子 Text 节点 | 是 |
| 抖音 PC | `douyin.exe` | 整树无类名，**键盘焦点定位** | 遍历子 Text 节点 | 是 |

客户端大版本更新可能改变控件类名，届时改 `config.json` 的 `targets[].input_class` 即可。

---

## 工作原理

两端思路一致：**不注入、不模拟点击、不自动发消息**，只做只读检测。

```
监听发送动作 ──> 确认输入框被清空 ──> 播放透明动画
```

**为什么用"输入框被清空"当发送信号**：这是唯一不依赖客户端实现细节的可靠信号——无论按回车、点发送按钮还是用输入法的发送键，消息发出后输入框都会空掉。只有"先命中过触发词、再在时间窗内清空"才算，手动退格删字不会误触发。

**电脑版**：低级键盘钩子（`WH_KEYBOARD_LL`）+ UI Automation 轮询前台窗口的聊天输入框，每个客户端一个适配器（`app/desktop/mjegg/im_targets.py`）。

**手机版**：`AccessibilityService` 监听 `TYPE_VIEW_TEXT_CHANGED`，跟踪聊天输入框文本；命中触发词后输入框变空即判定发送成功（`app/mobile/.../egg/EggTrigger.kt`，时钟可注入，核心逻辑有 JVM 单测覆盖）。

---

## 已知边界

- 电脑版需要聊天窗口在前台且能定位输入框；钉钉独立聊天窗口同样支持。
- QQ 是 Chromium 壳，默认不构建无障碍树，程序会按需"敲一下"让它暴露控件，失败时自动退回键盘缓冲兜底路径。
- 手机版若某客户端把输入框搬进 WebView，无障碍树可能读不到文本。
- HyperOS/MIUI 会冻结只挂无障碍服务的后台进程：不触发时先打开一次 App，或按保活页提示设置。
- 纯只读检测，账号风控风险低，但请理性使用。

---

## 致谢与许可

- 动画素材与触发规则源自 [qiu7c/Tiktok-MJ-for-Wechat](https://github.com/qiu7c/Tiktok-MJ-for-Wechat)（原作者 qiu7c）
- [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) —— OOBE 引导与部分动效参考
- [miuix](https://github.com/compose-miuix-ui/miuix) —— Jetpack Compose 版 HyperOS 设计组件库
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) —— 液态玻璃效果改编来源（Apache-2.0）
- [KernelSU](https://github.com/tiann/KernelSU) —— 悬浮底栏与界面配色参考

本仓库代码以 [MIT](LICENSE) 协议开源；`assets/` 内素材版权归原作者 / 权利方所有，仅供学习交流。
