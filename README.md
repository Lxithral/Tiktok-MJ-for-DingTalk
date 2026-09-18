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

| 平台 | 钉钉 | 微信 | QQ | 屏幕键盘 |
|---|---|---|---|---|
| **电脑版** (Windows) | ✅ 实测 8.5.5 | ✅ 实测 4.x | ✅ 实测 NT 版 | ✅ Windows 触摸键盘 / 屏幕键盘 |
| **手机版** (Android) | ✅ 无障碍 | ✅ 无障碍 | ✅ 无障碍 | ✅ 系统输入法即屏幕键盘 |

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

`app/mobile` 是一个标准的 Android 工程（Kotlin 2.2 + AGP 9 + Compose + miuix 组件库）。

```bash
cd app/mobile
./gradlew :app:assembleRelease      # 产出 app/build/outputs/apk/release/app-release.apk
```

安装后在 App 里按提示开启**无障碍服务**即可。细节见 **[app/mobile/README.md](app/mobile/README.md)**。

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
- 纯只读检测，账号风控风险低，但请理性使用。

---

## 致谢与许可

动画素材与触发规则源自 [qiu7c/Tiktok-MJ-for-Wechat](https://github.com/qiu7c/Tiktok-MJ-for-Wechat)（原作者 qiu7c）。

本仓库代码以 [MIT](LICENSE) 协议开源；`assets/` 内素材版权归原作者 / 权利方所有，仅供学习交流。
