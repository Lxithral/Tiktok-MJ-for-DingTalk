# MJ 彩蛋（手机版 / Android）

在**微信、QQ、钉钉**的聊天输入框里自己发送 `mj`、`mjmj`、`MJ`、`MjMj` 等组合时，全屏播放带透明通道的蜘蛛侠动画，播完自动消失。

> 总览与电脑版见仓库根目录的 [README](../../README.md)。

---

## 技术栈

| 项 | 取值 |
|---|---|
| 语言 | Kotlin 2.2.10 |
| 构建 | AGP 9.2.1 / Gradle 9.4.1 / compileSdk 37 / minSdk 31 / targetSdk 36 |
| UI | Jetpack Compose（BOM 2026.02.01），单 Activity |
| 组件库 | miuix `0.9.3`（`miuix-ui` / `miuix-preference` / `miuix-icons` / `miuix-blur`） |
| 持久化 | `SharedPreferences("settings")` |
| 不引入 | DI 框架、数据库、Navigation 库（HorizontalPager + 页索引即可） |

## 构建

```bash
cd app/mobile
./gradlew :app:assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk
```

**发布流程**：编译完把 APK 复制到桌面（`D:\desktop\MJ彩蛋手机版-v<版本>.apk`），
并执行 `app/desktop/poc/send_to_wechat.py <apk路径>` 发到微信「文件传输助手」，
方便手机直接取包安装。

- 需要 JDK 21（`JAVA_HOME` 指向 JDK 21）与 Android SDK（`local.properties` 里的 `sdk.dir`）。
- release 签名读 `~/.gradle/keystore.properties`；**该文件不存在时会自动回退到 debug 签名**，不会导致构建失败。
- 有意关闭了 R8 混淆：无障碍服务由系统反射实例化，且无法在本机真机验证混淆结果，保守起见保持关闭。

## 测试

核心判定逻辑做成了**可在 JVM 上跑**的纯逻辑，不依赖真机：

```bash
./gradlew :app:testDebugUnitTest      # 19 个用例
```

- `MatcherTest`（6 个）：触发词规则，含 README 规则表逐条比对，确保与电脑版 `matcher.py` 行为一致。
- `EggTriggerTest`（13 个）：触发状态机 —— 正常发送、逐字符输入、**退格删字不误触发**、
  超时不触发、点发送按钮快速通道、连续两次发送、重复上报只触发一次。

> 为此把 `EggTrigger` 从 `object` 改成了 `class`，时钟做成可注入的
> （`EggTrigger(clock = { ... })`），这样状态机才不依赖 `android.os.SystemClock` 而能单测。
> 这套测试确实抓到了一个真实缺陷：候选过期后没有被清掉，导致 `hasPending()` 永远为真，
> 上层会对每个内容变化事件都去做一次昂贵的焦点输入框读取。
>
> 注意 AGP 9 下只有 `testDebugUnitTest`，没有 `testReleaseUnitTest`。

## 用法

1. 安装 APK，打开 App。
2. 主页状态卡会提示"无障碍服务未开启"，点「去开启无障碍服务」跳到系统设置，在「无障碍 → 已安装的服务」里打开 **MJ 彩蛋**。
3. 回到 App，状态卡变绿（"彩蛋运行中"）。
4. 在微信 / QQ / 钉钉里发送 `mj`，动画即播。

功能页可以单独关闭某个应用、调音量 / 动画高度 / 冷却时间，也可以点「播放测试」直接放一遍。

---

## 架构

```
MainActivity
└── AppTheme(ThemeController)           # 深色四态 × Monet × 15 色预设
    └── MainScreen
        ├── Scaffold + BottomBar        # 三形态底栏(标准 / 悬浮 / 液态玻璃)
        └── HorizontalPager(4 页)
            ├── HomeScreen              # 大方块状态卡 + 快捷开关 + 触发规则
            ├── FeatureScreen           # 监控应用 + 播放参数 + 播放测试
            ├── ModuleScreen            # 素材与运行状态
            └── SettingsScreen ──> ThemeSettingsScreen

MjAccessibilityService                  # 系统持有的常驻服务(与本 App 同进程)
├── EggTrigger                          # 触发状态机(纯逻辑)
├── Matcher                             # 触发词规则(与电脑版一致)
└── EggOverlay                          # TYPE_ACCESSIBILITY_OVERLAY 播放层
```

### 触发检测

`MjAccessibilityService` 只处理配置里开启的目标包名，且只认"可编辑"的节点。

判据只有一条：

```
输入框命中触发词 + 输入框随后变空  ⇒  判定用户发送了消息
```

**这条判据不关心用户是怎么发的** —— 点客户端自带的发送按钮、按输入法的发送键、
按物理回车，消息发出后输入框都会空掉。所以不需要单独识别"发送按钮"
（微信就是用输入法的发送键，而不是 App 里的发送按钮）。

文本来源按三级回退，**不能只依赖 `event.source`**：

1. `event.source.text`（能拿到且节点可编辑时最准）
2. `event.text`（**注意：空列表表示"客户端没填"，不能当成空字符串**）
3. 焦点输入框 `rootInActiveWindow.findFocus(FOCUS_INPUT)`

同时用"事件 + 轮询"两条腿走路：

- 命中候选后启动 **80ms 高频轮询**焦点输入框，靠它捕捉"被清空"的那一瞬间
  （有些客户端清空输入框时并不派发事件）；候选失效后轮询自动停。
- 无候选时对内容/窗口变化事件做 **150ms 节流**的低频读取，
  兜住完全不派发 `TEXT_CHANGED` 的客户端。

安全约束（避免误判成"已清空"）：

- 读到的文本是 `null` 且当前没有候选 → 直接忽略，**绝不把 `null` 当空字符串**；
- 焦点节点不是可编辑控件 → 不上报（例如焦点跑到消息列表上）。

### 诊断页

设置 → 排查 → **诊断**。手机上没有真机调试环境时，这是唯一能看清"为什么没触发"的手段：

- **观察到的输入框节点**：客户端暴露的节点类名、是否 `isEditable`、文本能否读到
  （每个类名只记一次）。这一条最能说明问题 —— 如果这里压根没有微信/QQ 的节点，
  说明事件没进来；如果节点在但"文本可读=false"，说明客户端没把文本暴露给无障碍。
- **事件日志**：服务连接状态、目标应用发来的事件类型、事件里读到的文本、
  触发/冷却决策，倒序显示最近 400 条。
- 可一键清空。

### 播放层为什么不用悬浮窗权限

播放窗口用的是 **`TYPE_ACCESSIBILITY_OVERLAY`** —— 无障碍服务专属的窗口类型，**不需要** `SYSTEM_ALERT_WINDOW`（"显示在其他应用上层"）权限，也不需要悬浮窗授权弹窗。

三个 flag 保证"看得见但碰不到"：

```kotlin
FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS
```

动画素材是带 alpha 的动画 WebP，用 `ImageDecoder.decodeDrawable()` 解成 `AnimatedImageDrawable` 播放；音效从 `assets` 复制到 `cacheDir` 后用 `MediaPlayer` 播放（绕开 `openFd` 对压缩资源的限制）。播完由动画结束回调销毁窗口，另有 8 秒看门狗兜底。

两段动画交替播放，并按素材构图分别贴右上角（坠落）与左上角（荡绳），与电脑版一致。

### 主题系统

`AppTheme` 是唯一决定配色的地方，支持：

- **深色四态**：跟随系统 / 浅色 / 深色 / AMOLED（纯黑，用 `darkColorScheme()` 覆盖 `background`/`surface` 系列实现）
- **Monet 动态取色**：开关打开后由 miuix `ThemeController` 从种子色推导整套配色；种子色选「壁纸」时交给系统壁纸取色
- **15 色预设**：蓝 / 靛 / 紫 / 品红 / 玫红 / 朱红 / 橙 / 琥珀 / 黄绿 / 绿 / 青 / 天青 / 棕 / 灰蓝 + 壁纸
- **底栏三形态**：标准 miuix `NavigationBar` / `FloatingNavigationBar` / 悬浮 + `textureBlur` 玻璃（`GlassStrokeSmallLight/Dark` 高光描边，25f 模糊半径）
- **其余开关**：启用模糊、玻璃底栏、显示导航角标、预测性返回

所有开关都写进 `SharedPreferences("settings")`，杀进程重开依然保留。API < 33 或关闭模糊时，玻璃层自动降级为不透明。

> **预测性返回**：`AndroidManifest` 里声明了 `android:enableOnBackInvokedCallback="true"`，系统级预测动画默认生效；App 内的开关控制二级页是否跟随手势进度做转场。没有引入 `hiddenapibypass` 之类的隐藏 API 依赖，避免在无法真机验证的情况下引入运行时风险。

---

## 目录结构

```
app/mobile/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml            # 版本目录
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/                      # 动画 WebP + 音效(从 app/desktop/assets 复制而来, 保证本工程可独立构建)
        ├── res/xml/accessibility_service_config.xml
        └── java/com/lxithral/mjegg/
            ├── MainActivity.kt
            ├── platform/SettingsStore.kt      # SharedPreferences + Compose 状态
            ├── egg/
            │   ├── MjAccessibilityService.kt  # 无障碍服务
            │   ├── EggTrigger.kt              # 触发状态机
            │   ├── EggOverlay.kt              # 无障碍覆盖层播放
            │   └── Matcher.kt                 # 触发词规则
            └── ui/
                ├── MainScreen.kt
                ├── theme/{AppTheme,Palette}.kt
                ├── navigation/BottomBar.kt
                ├── component/StatusCard.kt
                └── screen/{home,feature,module,settings}/…
```

---

## 版本记录

> ⚠️ **当前状态：尚未跑通。** 三个客户端（微信 / QQ / 钉钉）都还没触发成功。
> 下面记录已定位并修掉的缺陷，以及仍未验证的部分。

### 1.0.2 — 修复"服务根本没连上"

**根因：清单里 `android:exported` 写成了 `false`。**

```xml
<!-- 错误 -->
<service android:name=".egg.MjAccessibilityService" android:exported="false" ... />
<!-- 正确 -->
<service android:name=".egg.MjAccessibilityService" android:exported="true" ... />
```

无障碍服务是**由 `system_server` 从应用进程之外绑定的**，`exported="false"` 会被系统
拒绝绑定。这个错误的症状极具迷惑性：

- 系统设置里**能正常看到并勾选**（开关状态存在 `Settings.Secure`，与绑定成功无关）
- 但 `onServiceConnected()` 永不回调，**一个事件都收不到**
- 表现就是"三个客户端全都不触发"，且**换多少种事件读取逻辑都没用**

安全性不受影响：`android:permission="BIND_ACCESSIBILITY_SERVICE"` 保证只有系统能绑定。

同时把 UI 上"设置里已勾选"和"实际已连接"拆成两个独立状态显示 —— 二者不一致就是绑定失败，
主页会直接提示「服务已勾选, 但没连上，去无障碍设置里关掉再重新打开一次」。

### 1.0.1 — 修复事件读取

两个缺陷，各自都足以导致全灭：

1. **`event.source` 为 null 时直接 `return`**。Android 上 `TYPE_VIEW_TEXT_CHANGED`
   事件的 `source` 在很多机型/客户端上是 null，早期版本一遇到就直接放弃，
   等于永远收不到文本。改为三级回退（source → event.text → 焦点输入框）。
2. **把 `event.text` 的空列表当成了空字符串**。`getText()` 返回的 List 为空表示
   "客户端没填这个字段"，不是"文本为空"。早期写法 `joinToString("").orEmpty()`
   把每次事件都误判成"输入框已清空"。

同时把"清空"的观测从"纯事件驱动"改成"事件 + 轮询"（候选期内 80ms 轮询焦点输入框），
并新增**诊断页**用于在没有真机调试环境时定位问题。

### 1.0.0 — 首个版本

## 已知边界

- **三个客户端都还没跑通**：`android:exported` 这个根因已修（v1.0.2），但**尚未在真机上验证**。
  装机后请先看主页状态卡是否为「彩蛋运行中」；若显示「服务已勾选, 但没连上」，
  去无障碍设置里关掉再重新打开一次。
- 若仍不触发，请提供 **设置 → 排查 → 诊断** 里的日志。那一页能直接区分三种情况：
  没有目标应用的事件（服务没连上/被 ROM 限制）、有事件但文本读不到（客户端没暴露文本）、
  有文本但判据没走通。
- 各客户端大版本更新可能改变输入框实现；检测用的是 `isEditable` + 类名 + `ACTION_SET_TEXT`
  三重判断而不是硬编码资源 id，鲁棒性较好，但若某客户端改用 WebView 承载输入框，无障碍树可能读不到文本。
- 部分厂商 ROM（MIUI/HyperOS、ColorOS 等）会在息屏或后台限制无障碍服务，
  需要把本 App 加入电池优化白名单 / 允许后台运行。
- 纯只读检测：不注入按键、不模拟点击、不自动发消息。

## 致谢

动画素材与玩法源自 [qiu7c/Tiktok-MJ-for-Wechat](https://github.com/qiu7c/Tiktok-MJ-for-Wechat)。
