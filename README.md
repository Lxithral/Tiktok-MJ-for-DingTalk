# MJ 彩蛋 · 多端（钉钉 / 微信 / QQ）

在聊天窗口里**自己发送** `mj`、`mjmj`、`MJ`、`MjMj` 等组合时，全屏播放一段带透明通道的蜘蛛侠动画，播完自动消失，不抢焦点、不影响打字。

玩法与动画素材源自 iOS 越狱插件 [qiu7c/Tiktok-MJ-for-Wechat](https://github.com/qiu7c/Tiktok-MJ-for-Wechat)（抖音 MJ 蜘蛛侠彩蛋）。

```
MJ-DingTalk/
├── app/
│   ├── desktop/     # 电脑版：Python + PyQt6（Windows）
│   └── mobile/      # 手机版：Kotlin + Jetpack Compose + miuix（Android）
├── README.md
└── LICENSE
```

---

## 支持矩阵

| 平台 | 钉钉 | 微信 | QQ | 抖音 | 屏幕键盘 |
|---|---|---|---|---|---|
| **电脑版** (Windows) | ✅ 实测 8.5.5 | ✅ 实测 4.x | ✅ 实测 NT 版 | — | ✅ Windows 触摸键盘 / 屏幕键盘 |
| **手机版** (Android) | ✅ 实测 | ✅ 实测 | ✅ 实测 | ✅ 实测（私信） | ✅ 系统输入法即屏幕键盘 |

两端共用同一套触发词规则与同一份动画素材。

---

## 电脑版

**快速开始**：双击 `app/desktop/dist/MJDingTalk.exe`，或 `app/desktop/启动彩蛋.bat`。

启动后驻留系统托盘（红底 MJ 图标），右键菜单有 **启用/停用彩蛋、播放测试、开机自启、关于、退出**。

**源码运行**：

```bat
cd app/desktop
pip install PyQt6 uiautomation pywin32 Pillow
python main.py
```

**打包**：双击 `app/desktop/build_exe.bat` → 产出 `dist/MJDingTalk.exe` + `dist/MJDingTalk-green-v1.3.0.zip`。

细节（各客户端输入框识别方式、屏幕键盘注入策略、素材制作、配置项）见 **[app/desktop/README.md](app/desktop/README.md)**。

---

## 手机版

> ✅ **当前状态：四端（微信 / QQ / 钉钉 / 抖音）均已真机实测触发。**
>
> 手机版在真机上踩了一串缺陷后修通，版本记录见 `app/mobile/README.md`：
> v1.0.1（空 source / 空列表误判）→ v1.0.2（`exported=false` 导致服务没被系统绑定）→
> v1.0.3（输入清洗 + 诊断转义）→ v1.0.7（**微信窗口树对无障碍不可见**，改用事件自带
> text 判定"命中→清空"）。
>
> 若不触发，先看 App 主页状态卡（「彩蛋运行中」= 服务连上了），再到
> **设置 → 诊断** 看逐环结论 —— 那一页能直接区分"事件没进来"、"文本读不到"、
> "命中过但没观察到发送"。HyperOS 会冻结只挂无障碍服务的后台进程，
> 不触发时先打开一次 App（或关掉电池优化）。

`app/mobile` 是一个标准的 Android 工程（Kotlin 2.2 + AGP 9 + Compose + miuix 组件库）。

```bash
cd app/mobile
./gradlew :app:assembleRelease      # 产出 app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest    # 19 个 JVM 单测(触发词规则 + 触发状态机)
```

安装后在 App 里按提示开启**无障碍服务**即可。细节见 **[app/mobile/README.md](app/mobile/README.md)**。

---

## 验证情况

**电脑版 —— 三端全部实机回归（2026-09-25，端到端真发消息）**

- ✅ 微信 4.x / QQ NT / **钉钉**：真实发送 `mj` 均触发，动画锚定正确（坠落右上 / 荡绳左上）、自动关闭
- ✅ Windows 触摸键盘：点 `m` `j` 回车发送同样触发
- ✅ 键盘缓冲兜底路径：故意写错输入框类名后仍能触发
- ✅ 负向用例：`amj`、`mjm`、退格删字、中文输入法"上屏"都不触发

**手机版 —— 三端全部实机实测触发**

- ✅ 微信：v1.0.7 修复触发链路后真机实测触发（详见上方手机版章节与 mobile README 版本记录）
- ✅ QQ：链路正常，真机实测触发
- ✅ 钉钉：v1.0.9 修复后真机实测触发（清空事件的 text 与节点 text 都返回占位 hint，见 v1.0.9）
- ✅ 抖音：v1.0.10 新增第四目标，真机实测触发（发到 Yunnki 会话，命中/确认链路全通）
- ✅ 编译通过、release 已签名；核心判定逻辑有 19 个 JVM 单测覆盖

---

## 触发规则（两端一致）

长度为偶数且每 2 个字符一组均为 `mj`（不区分大小写）：

| 输入 | 结果 |
|------|------|
| `mj` / `MJ` / `Mj` | ✅ 触发 |
| `mjmj` / `MJMJ` / `MjMj` | ✅ 触发 |
| `mjm` / `mjx` / `ajmj` / `amj` | ❌ 不触发 |
| 中文 / 其他文字 | ❌ 不触发 |

只响应**自己发送**的消息：对方发的 `mj` 不会触发（两端检测的都是"你正在发送"这个动作）。

**中文输入法**：中文模式下输入 `mj` 再按回车，只是把字母**上屏**进输入框（消息并未发送），**不会触发**；再按一次回车真正发送出去时才触发。

---

## 工作原理

两端思路一致：**不注入、不模拟点击、不自动发消息**，只做只读检测。

```
监听发送动作 ──> 确认输入框被清空 ──> 播放透明动画
```

**电脑版**：低级键盘钩子（`WH_KEYBOARD_LL`）+ UI Automation 轮询前台窗口的聊天输入框。三个客户端的技术栈不同，输入框的暴露方式也不同，因此每个客户端一个适配器（见 `app/desktop/mjegg/im_targets.py`）。

**手机版**：`AccessibilityService` 监听 `TYPE_VIEW_TEXT_CHANGED`，跟踪聊天输入框的文本；命中触发词后输入框变空即判定为发送成功（见 `app/mobile/app/src/main/java/com/lxithral/mjegg/egg/EggTrigger.kt`）。

**为什么用"输入框被清空"当发送信号**：这是唯一不依赖客户端实现细节的可靠信号 —— 无论按回车、点发送按钮还是用输入法的发送键，消息发出后输入框都会空掉。反过来，只有"先命中过触发词、再在时间窗内清空"才算，手动退格删字不会误触发。

---

## 已知边界

- 电脑版需要**当前会话窗口在前台**且能定位到聊天输入框；钉钉独立聊天窗口同样支持。
- QQ 是 Chromium 壳，默认不构建无障碍树。程序会在需要时"敲一下"让它暴露控件（`wake_a11y`）；万一失败会自动退回键盘缓冲兜底路径。
- 客户端大版本更新可能改变控件类名，届时改 `config.json` 里的 `targets[].input_class` 即可（电脑版）。
- 手机版若某客户端把输入框搬到 WebView 里承载，无障碍树可能读不到文本。
- 纯只读检测，账号风控风险低，但请理性使用。

---

## 致谢与许可

动画素材与触发规则源自 [qiu7c/Tiktok-MJ-for-Wechat](https://github.com/qiu7c/Tiktok-MJ-for-Wechat)（原作者 qiu7c）。

本仓库代码以 [MIT](LICENSE) 协议开源；`assets/` 内素材版权归原作者 / 权利方所有，仅供学习交流。
