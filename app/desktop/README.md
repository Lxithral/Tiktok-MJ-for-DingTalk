# 钉钉 / 微信 / QQ MJ 彩蛋（电脑版）

在**钉钉 PC、微信 4.x、QQ NT** 的聊天输入框里自己发送 `mj`、`mjmj`、`MJ`、`MjMj` 等组合时，全屏播放带透明通道的蜘蛛侠动画。

动画窗口**点击穿透、不抢焦点**，不影响打字和聊天；原版音效同步播放；播完自动消失。

> 总览与手机版见仓库根目录的 [README](../../README.md)。

## 快速开始

**方式一（推荐）**：双击 `dist\MJDingTalk.exe`，或双击 `启动彩蛋.bat`（优先启动 exe）。

**方式二（源码运行）**：

```bat
pip install PyQt6 uiautomation pywin32 Pillow
python main.py
```

启动后驻留在系统托盘（红底 MJ 图标）：右键菜单有 **启用/停用彩蛋、播放测试、开机自启、关于、退出**。

"关于"窗口展示应用图标、名称、版本号（v1.3.0）和开发者，**深浅色跟随系统**并实时切换。

在钉钉 / 微信 / QQ 任意聊天输入框输入 `mj`（或 `mjmj`/`MJMJ`…）按回车发送，动画即播。两段动画自动交替，并按素材构图锚定角落：**坠落**贴屏幕**右上角**，**荡绳**贴屏幕**左上角**（荡绳素材的蛛丝悬挂点在画面左边界之外，贴左播放才能与屏幕边缘自然衔接）。原视频音效同步播放。

## 各客户端的输入框适配

三个客户端技术栈完全不同，输入框的暴露方式也不同（2026-09 实测）：

| 客户端 | 进程 | 输入框 UIA 类名 | 控件类型 | 读文本方式 | 需要唤醒 |
|---|---|---|---|---|---|
| 钉钉 | `DingTalk.exe` | `im_chat::InputRichTextEdit` | Edit | ValuePattern | 否 |
| 微信 4.x | `Weixin.exe` | `mmui::ChatInputField` | Edit | ValuePattern | 否 |
| QQ NT | `QQ.exe` | `ExEditor-qq-msg-editor` | Group（ProseMirror） | 遍历子 Text 节点 | **是** |

要点：

- **微信**输入框类名是精确值，用类名全等匹配即可；同窗口里还有个 `mmui::XValidatorTextEdit`（搜索框），别搞混。
- **QQ** 编辑器的 ClassName 是一整串，例如 `ProseMirror ExEditor-qq-msg-editor is-empty`，其中 `is-empty` 会随输入状态增减，所以只能用**子串匹配**（配置里的 `"match": "contains"`）。
- **QQ 是 Chromium 壳，默认根本不构建无障碍树**（整棵树只有十几个节点）。程序会在定位失败时"敲一下"（`WM_GETOBJECT` + 前台激活）促使渲染进程建树，成功后再缓存节点。万一唤醒失败，会自动退回键盘缓冲兜底路径，功能不中断。

## Windows 屏幕键盘 / 触摸键盘支持

**Windows 屏幕键盘（`osk.exe`）和触摸键盘（`TabTip.exe`）都是通过 `SendInput` 注入按键的**，实测这类按键带 `LLKHF_INJECTED(0x10)` 标志：

```
触摸键盘点 m     -> vk=0x4D scan=0x32 flags=0x10 INJ extra=0x0
触摸键盘点 j     -> vk=0x4A scan=0x24 flags=0x10 INJ extra=0x0
触摸键盘点回车   -> vk=0x0D scan=0x1C flags=0x10 INJ extra=0x0
```

所以"一律忽略注入按键"的老做法会把屏幕键盘也一起挡掉。但按键精灵之类的宏工具同样走 `SendInput`，又不能全放开。折中策略由 `injected_key_policy` 控制：

| 取值 | 行为 |
|---|---|
| `ignore` | 从不接受注入按键（屏幕键盘不可用） |
| `unsigned` | 只接受 `dwExtraInfo == 0` 的注入 |
| `auto`（默认） | `unsigned`，且当检测到屏幕键盘进程正在运行时放宽为接受全部注入 |
| `allow` | 全部接受 |

`auto` 里的"屏幕键盘进程"由检测线程定期探测（`screen_keyboard_processes`），钩子回调只读一个布尔量，不做系统调用，保证回调足够快。被忽略的注入按键会打一条带提示的日志，方便自查。

**中文输入法**下用屏幕键盘敲 `mj` 再回车，同样只是"上屏"不算发送 —— 回车那一瞬间会检查输入法组合态（`ImmGetCompositionString`），有未上屏的候选就判定为"提交候选词"，把缓冲搬到 pending 等真正的发送键。

## 打包为 exe

```bat
双击 build_exe.bat
```

或手动：

```bat
pip install pyinstaller
python -m PyInstaller --noconfirm --clean --noconsole --onefile --icon assets/app.ico --add-data "assets;assets" --name MJDingTalk main.py
```

产物为 `dist\MJDingTalk.exe`（单文件、无终端窗口，约 78MB），同时打包 `dist\MJDingTalk-green-v1.3.0.zip`（内含 exe + config.json，解压即用）。素材打包在 exe 内部；**config.json 和日志生成在 exe 旁边**（首次运行自动创建），想改配置就改 exe 旁边那份。onefile 首次启动需解压，等 1~3 秒属正常；如被杀软拦截，添加信任即可。发新版本时记得更新 `build_exe.bat` 顶部的 `VERSION` 变量。

## 触发规则

长度为偶数且每 2 个字符一组均为 "mj"（不区分大小写）：

| 输入 | 结果 |
|------|------|
| `mj` / `MJ` / `Mj` | ✅ 触发 |
| `mjmj` / `MJMJ` / `MjMj` | ✅ 触发 |
| `mjm` / `mjx` / `ajmj` / `amj` | ❌ 不触发 |
| 中文/其他文字 | ❌ 不触发 |

只触发**自己发送的**消息（对方发的 mj 不触发）。历史消息天然不涉及——检测的是"你正在发送"这个动作。

**中文输入法行为**：中文模式下输入 `mj` 再按回车，只是把字母"上屏"进输入框（消息并未发送），**不会触发**；再按一次回车真正发送出去（输入框被清空）时才触发。判定原理：触发必须同时满足"输入框命中过触发词 + 输入框被清空 + 发送键刚按下"三个条件，"清空"是消息真正发出的唯一可靠信号。

## 工作原理

原项目 Hook 微信私有类消息函数实现"生产者(pending)-消费者(渲染)两段式"；桌面版没有注入条件，等价替换为：

```
键盘钩子(WH_KEYBOARD_LL)                 UIA 轮询(60ms)
  Enter / Alt+S 按下 ──┐                  前台进程命中时读输入框
                       v                        v
              [路径A·即时] 读输入框文本 ──命中──> 登记候选
              [路径B·兜底] "命中过触发词→输入框被清空→发送键刚按下" ──> 触发
                                                      |
                                          PyQt6 透明点击穿透全屏窗口
                                          QMovie 播放带 alpha 的动画 WebP
```

- **路径 A**：Enter 瞬间立即读输入框，命中即登记候选（零竞态，最快）
- **路径 B**：兜住"客户端先清空输入框后我们才读到"的竞态，以及点击发送按钮的场景
- **键盘兜底**：输入框 UIA 不可读时（例如 Chromium 壳的无障碍树没起来），退化为"键盘缓冲整串命中"。缓冲的语义是"从上次发送之后敲进输入框的全部内容"，所以做**整串**匹配而不是尾部模糊匹配 —— 否则 `amj` 会因为结尾是 `MJ` 被误判（原规则里 `amj` 不该触发）
- 全程**只读**前台窗口名、输入框文本和键盘事件流，**不注入目标进程、不模拟点击、不自动发消息**
- 4 秒冷却防止连发刷屏；播完自动销毁窗口 + 30 秒看门狗兜底回收

素材制作与原项目相同：仓库内的双画面蒙版源视频（左灰度蒙版+右彩色画面）经 ffmpeg `alphamerge` 合成带透明通道的画面。注意**不能直接用 ffmpeg 的 webp muxer 一步转动画 WebP**——libwebp 动画编码器对透明背景做子矩形增量编码，旧位置的图案不会被擦除，播放时会叠成一串（实测踩坑）。正确做法是先逐帧导出 PNG，再用 Pillow 以"全帧替换"（`disposal=2`）组装动画 WebP：

```bash
# 1. 逐帧导出 PNG (带 alpha)
ffmpeg -i assets_src/mj-drop-dual-mask.mp4 -filter_complex \
"[0:v]split[x][y];[y]crop=iw/2:ih:0:0,format=gray[m];[x]crop=iw/2:ih:iw/2:0[c];[c][m]alphamerge,format=rgba[out]" \
-map "[out]" build_frames/drop_%03d.png

# 2. Pillow 组装 (帧时长 33ms, 全帧替换)
python -c "
from PIL import Image; import glob
fs = [Image.open(f).convert('RGBA') for f in sorted(glob.glob('build_frames/drop_*.png'))]
fs[0].save('assets/mj-drop-alpha.webp', save_all=True, append_images=fs[1:], duration=33, loop=0, disposal=2, minimize_size=False, quality=85, method=4)
"
```

## 配置 (config.json)

| 键 | 默认 | 说明 |
|----|------|------|
| `enabled` | `true` | 启动时是否启用 |
| `targets` | 见下 | 支持的目标客户端列表 |
| `poll_interval_ms` | `60` | 输入框轮询间隔 |
| `send_key_window_s` | `0.8` | 发送键有效时间窗 |
| `match_window_s` | `1.2` | 输入框命中触发词后的有效时间窗 |
| `cooldown_s` | `4.0` | 两次播放最小间隔 |
| `overlay_height_ratio` | `0.85` | 动画高度占屏幕高度比例 |
| `volume` | `1.0` | 音效音量（0.0~1.0，改 0 可静音） |
| `watchdog_s` | `30` | 播放僵尸会话强制回收 |
| `injected_key_policy` | `"auto"` | 注入按键策略，见上文（`ignore`/`unsigned`/`auto`/`allow`） |
| `screen_keyboard_processes` | `osk.exe` 等 | 视为"用户在用屏幕键盘"的进程名 |
| `fallback_vk_buffer` | `true` | 输入框 UIA 不可读时的键盘缓冲兜底 |
| `ime_aware` | `true` | 回车遇输入法组合态时判为"上屏"而非"发送" |

`targets` 每一项：

```json
{
  "process": "QQ.exe",
  "input_class": "ExEditor-qq-msg-editor",
  "match": "contains",
  "wake_a11y": true
}
```

- `match`：`exact`（默认，类名全等）/ `contains`（类名子串）
- `wake_a11y`：`true` 时在定位失败后尝试唤醒 Chromium 系客户端的无障碍树

> 旧版配置里的 `process_names` + `input_control_class`、以及 `ignore_injected_keys` 会自动迁移到新结构，不用手改。

## 已验证

- ✅ 微信 4.x / QQ NT / 钉钉 三端实测（2026-09-25 端到端回归，`poc/test_trigger.py` 真发消息）：
  发送 `mj` 均触发，动画锚定正确、自动关闭
- ✅ Windows 触摸键盘实测：点 `m` `j` 回车发送同样触发
- ✅ 键盘兜底路径实测：故意写错输入框类名后仍能触发；`amj` 负向用例不触发
- ✅ 触发词规则、冷却防重、多实例互斥
- ✅ 真实屏幕透明合成渲染（截图目检）
- ✅ 素材无叠影：源视频逐帧比对 + PIL/ffmpeg 双解码器验证单帧干净

## 已知边界

- 需要**当前会话窗口在前台**且能定位到聊天输入框（独立聊天窗口同样支持）
- 用鼠标点"发送"按钮发送 mj：走路径 B，可触发（前提是之前 1.2 秒内输入框被轮询命中过）
- 客户端大版本更新可能改变控件类名，届时调整 `config.json` 的 `targets[].input_class`
- 纯只读检测，账号风控风险低，但请理性使用

## 目录结构

```
app/desktop/
├── main.py            # 入口: 托盘 + 装配
├── config.json        # 配置 (exe 旁边会自动生成同名文件)
├── build_exe.bat      # 一键打包 exe
├── 启动彩蛋.bat       # 优先启动 exe, 无则源码运行
├── mjegg/
│   ├── config.py      # 配置加载 + 旧配置迁移
│   ├── paths.py       # 源码/打包双形态资源与可写目录适配
│   ├── theme.py       # 深浅色跟随系统(实时切换)
│   ├── about.py       # 关于窗口
│   ├── splash.py      # 启动 splash
│   ├── matcher.py     # 触发词匹配
│   ├── im_targets.py  # 多客户端输入框适配(钉钉/微信/QQ)
│   ├── ime.py         # 输入法组合态检测
│   ├── foreground.py  # 前台窗口进程判断 / 进程存在性探测
│   ├── keyhook.py     # WH_KEYBOARD_LL 键盘钩子线程
│   ├── watcher.py     # 检测 worker
│   └── overlay.py     # 透明点击穿透播放窗口
├── assets/            # 带 alpha 的动画素材 + 音效 + 头像 + 图标
├── assets_src/        # 原始双画面蒙版源视频
├── dist/MJDingTalk.exe
└── poc/               # POC 与测试脚本
    ├── probe_*.py            # UIA 树/输入框探测
    ├── keylog.py             # 按键取证(区分屏幕键盘与宏工具注入)
    ├── test_trigger.py       # 端到端触发测试
    ├── tabtip_e2e.py         # 触摸键盘端到端测试
    ├── verify_phone_dingtalk.py  # 手机版钉钉触发验证(ADB 驱动, 手机连上后一条命令)
    └── send_to_wechat.py     # 发文件/文字到微信「文件传输助手」(已退出交付流程, 工具保留)
```

## 致谢与许可

动画素材与触发规则源自 [qiu7c/Tiktok-MJ-for-Wechat](https://github.com/qiu7c/Tiktok-MJ-for-Wechat)（iOS 越狱插件，原作者 qiu7c）。

本仓库代码以 [MIT](../../LICENSE) 协议开源；`assets/` 内素材版权归原作者/权利方所有，仅供学习交流。
