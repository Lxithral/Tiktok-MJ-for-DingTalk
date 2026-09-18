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

`MjAccessibilityService` 监听 `TYPE_VIEW_TEXT_CHANGED`，只处理配置里开启的目标包名，且只认"可编辑"节点（`isEditable` 或类名含 `EditText`）：

```
输入框文本变化
  ├── 非空 且 命中触发词  -> 记为候选(记下时间)
  ├── 非空 但 不命中      -> 撤销候选
  └── 变空 且 候选在 8 秒内 -> 判定"用户发送了消息" -> 播动画
```

另外两条辅助路径：

- **点发送按钮**：`TYPE_VIEW_CLICKED` 且节点文案是「发送 / Send」时，立即确认候选（不必等输入框清空）。
- **内容变化兜底**：部分客户端不派发 `TEXT_CHANGED`。这时在 `TYPE_WINDOW_CONTENT_CHANGED` 里用 `findFocus(FOCUS_INPUT)` 读一次焦点输入框 —— **只在已有候选命中时才做**，平时零开销。

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

## 已知边界

- **尚未在真机回归**：工程按 `./gradlew :app:assembleRelease` 可编译通过，核心判定逻辑有
  JVM 单测覆盖，APK 结构（清单 / 服务声明 / 权限 / 素材）已用 `aapt2` 逐项核对，
  但本机没有连接 Android 设备，触发链路的实机表现（各客户端输入框节点类型、事件派发频率）
  需要装机后验证。
- 微信 / QQ / 钉钉的**大版本更新**可能改变输入框的控件类型；由于检测用的是 `isEditable` 而不是硬编码资源 id，鲁棒性较好，但若某客户端改用 WebView 承载输入框，无障碍树可能读不到文本。
- 部分厂商 ROM 会在息屏 / 后台限制无障碍服务，需要把本 App 加入电池优化白名单。
- 纯只读检测：不注入按键、不模拟点击、不自动发消息。

## 致谢

动画素材与玩法源自 [qiu7c/Tiktok-MJ-for-Wechat](https://github.com/qiu7c/Tiktok-MJ-for-Wechat)。
