# -*- coding: utf-8 -*-
"""多 IM 目标适配: 钉钉 / 微信 / QQ 的聊天输入框定位与读取.

各客户端的技术栈不同, 输入框的暴露方式也不同 (2026-09 实测):

| 客户端 | 进程 | 输入框 UIA 类名 | 控件类型 | 读文本方式 |
|---|---|---|---|---|
| 钉钉   | DingTalk.exe | `im_chat::InputRichTextEdit` | EditControl | ValuePattern |
| 微信 4.x | Weixin.exe | `mmui::ChatInputField` | EditControl | ValuePattern |
| QQ NT  | QQ.exe | `ExEditor-qq-msg-editor` | GroupControl (ProseMirror) | 遍历子 Text 节点 |

QQ 是 Chromium 壳, 默认**不构建无障碍树**(只有十几个节点); 必须先用
"前台激活 + 请求 UIA 对象" 敲一下, 渲染进程才会开始暴露真实控件树。
"""
import ctypes
import ctypes.wintypes as wt
import logging
import time

import uiautomation as uia

log = logging.getLogger("mjegg.targets")

user32 = ctypes.windll.user32

WM_GETOBJECT = 0x003D
OBJID_CLIENT = 0xFFFFFFFC

# 内置默认目标 (config.json 的 targets 会覆盖)
#
# match 字段: "exact" = ClassName 全等匹配; "contains" = ClassName 包含子串.
# QQ 编辑器的 ClassName 是一整串(如 "ProseMirror ExEditor-qq-msg-editor is-empty"),
# 且 is-empty 会随输入状态增减, 所以只能用 contains.
DEFAULT_TARGETS = [
    {"process": "DingTalk.exe", "input_class": "im_chat::InputRichTextEdit"},
    {"process": "Weixin.exe", "input_class": "mmui::ChatInputField"},
    {"process": "QQ.exe", "input_class": "ExEditor-qq-msg-editor",
     "match": "contains", "wake_a11y": True},
]

MAX_SEARCH_DEPTH = 32
WALK_NODE_BUDGET = 4000      # contains 模式手工遍历的节点上限(防止极端 UI 卡死)
WALK_MIN_INTERVAL_S = 0.8    # 手工遍历的最小间隔(失败时不要每次轮询都全树扫)


class InputTarget:
    """一个 IM 客户端的输入框适配器."""

    def __init__(self, spec):
        self.process = spec["process"]
        self.input_class = spec["input_class"]
        self.match_mode = spec.get("match", "exact")
        self.wake_a11y = bool(spec.get("wake_a11y", False))
        self._node = None
        self._top_hwnd = 0
        self._wake_attempts = 0
        self._last_wake_ts = 0.0
        self._last_walk_ts = 0.0

    # ---------- 进程匹配 ----------
    def matches(self, process_name) -> bool:
        return bool(process_name) and process_name.lower() == self.process.lower()

    # ---------- 定位 ----------
    def locate(self, top_hwnd, force=False):
        """返回输入框 UIA 节点(带缓存). 找不到返回 None."""
        if not top_hwnd:
            return None
        if self._node is not None and not force and self._top_hwnd == top_hwnd:
            return self._node
        try:
            top = uia.ControlFromHandle(top_hwnd)
            if top is None:
                return None
        except Exception:
            self.invalidate()
            return None
        node = self._search(top)
        if node is None and self.wake_a11y:
            node = self._wake_and_search(top_hwnd)
        if node is None:
            return None
        self._node = node
        self._top_hwnd = top_hwnd
        log.info("[%s] 输入框已定位 cls=%s", self.process, self.input_class)
        return node

    def _search(self, top):
        if self.match_mode in ("exact", "auto"):
            node = self._search_exact(top)
            if node is not None:
                return node
        if self.match_mode in ("contains", "auto"):
            return self._search_contains(top)
        return None

    def _search_exact(self, top):
        try:
            # 注意: 这里必须用通用的 Control 搜索(按类名), 不能按控件类型过滤 ——
            # 微信的输入框是 Edit, QQ 的是 Group, 用 EditControl 会漏掉 QQ.
            found = top.Control(searchDepth=MAX_SEARCH_DEPTH,
                                ClassName=self.input_class)
            if found is not None and found.Exists(0.15, 0.05):
                return _retype(found)
        except Exception as e:
            log.debug("[%s] 精确搜索输入框异常: %s", self.process, e)
        return None

    def _search_contains(self, top):
        """手工深度优先遍历, 按 ClassName 子串匹配.

        不能每次轮询都扫全树, 所以限制最小间隔.
        """
        now = time.time()
        if now - self._last_walk_ts < WALK_MIN_INTERVAL_S:
            return None
        self._last_walk_ts = now
        needle = self.input_class
        budget = [WALK_NODE_BUDGET]
        hit = []

        def walk(node, depth):
            if hit or depth > MAX_SEARCH_DEPTH or budget[0] <= 0:
                return
            try:
                children = node.GetChildren()
            except Exception:
                return
            for c in children:
                if hit or budget[0] <= 0:
                    return
                budget[0] -= 1
                try:
                    if needle in (c.ClassName or ""):
                        hit.append(c)
                        return
                except Exception:
                    pass
                walk(c, depth + 1)

        try:
            walk(top, 1)
        except Exception as e:
            log.debug("[%s] 遍历搜索异常: %s", self.process, e)
        return _retype(hit[0]) if hit else None

    def _wake_and_search(self, top_hwnd):
        """Chromium 系(QQ)唤醒: 请求顶层窗口的 UIA 对象 + 前台激活, 促使渲染进程建树."""
        now = time.time()
        if self._wake_attempts >= 3 or now - self._last_wake_ts < 2.0:
            return None
        self._wake_attempts += 1
        self._last_wake_ts = now
        log.info("[%s] 无障碍树未就绪, 尝试唤醒 (%d/3)", self.process, self._wake_attempts)
        try:
            user32.SendMessageW(top_hwnd, WM_GETOBJECT, 0,
                                ctypes.c_void_p(OBJID_CLIENT))
        except Exception:
            pass
        # 只有当该进程本来就是前台时才调用(避免抢走用户焦点): 此时是 no-op 但会触发激活事件
        try:
            fg = user32.GetForegroundWindow()
            if fg == top_hwnd:
                user32.SetForegroundWindow(top_hwnd)
        except Exception:
            pass
        time.sleep(0.9)
        try:
            top = uia.ControlFromHandle(top_hwnd)
            self._last_walk_ts = 0.0  # 唤醒后允许立即遍历一次
            return self._search(top)
        except Exception:
            return None


    def invalidate(self):
        self._node = None
        self._top_hwnd = 0

    # ---------- 读取 ----------
    def read_text(self, node):
        """读输入框当前文本. 空输入框返回 "". 读不到返回 None(触发兜底路径)."""
        if node is None:
            return None
        try:
            if hasattr(node, "GetValuePattern"):
                # 控件类型支持 ValuePattern(钉钉/微信): 直接取值.
                # 节点失效(客户端重建了控件)时会抛异常 -> 走 except 让调用方重新定位.
                vp = node.GetValuePattern()
                if vp is None:
                    self.invalidate()
                    return None
                return vp.Value or ""
            # 不支持 ValuePattern(QQ 的 ProseMirror 编辑器): 遍历子 Text 节点拼文本
            return self._read_descendants(node)
        except Exception as e:
            log.debug("[%s] 读输入框失败(节点可能已失效): %s", self.process, e)
            self.invalidate()
            return None

    @staticmethod
    def _read_descendants(node, max_depth=6):
        """遍历子 Text 节点拼出文本 (QQ ProseMirror 编辑器用).

        根节点失效时必须把异常抛出去, 否则会被误当成"输入框是空的".
        """
        parts = []

        def walk(n, depth):
            if depth > max_depth:
                return
            try:
                children = n.GetChildren()
            except Exception:
                if depth == 1:
                    raise
                return
            for c in children:
                try:
                    if c.ControlTypeName == "TextControl":
                        nm = c.Name or ""
                        if nm.strip():
                            parts.append(nm)
                        continue
                except Exception:
                    continue
                walk(c, depth + 1)

        walk(node, 1)
        return "".join(parts).strip()


def _retype(node):
    """把通用 Control 还原成真实控件类型.

    `Control(ClassName=...)` 搜出来的是基类 Control, 它没有 GetValuePattern 之类
    的类型化方法(踩坑: 微信输入框因此永远读到空串). 用控件真实类型重建一次即可.
    """
    if node is None:
        return None
    try:
        typed = uia.Control.CreateControlFromElement(node.Element)
        return typed if typed is not None else node
    except Exception:
        return node



def build_targets(specs):
    """把 config 里的 targets 列表变成适配器列表."""
    out = []
    for spec in specs or []:
        if not isinstance(spec, dict) or "process" not in spec or "input_class" not in spec:
            log.warning("忽略非法 target 配置: %r", spec)
            continue
        out.append(InputTarget(spec))
    return out


def find_target(process_name, targets):
    """按前台进程名找适配器."""
    if not process_name:
        return None
    low = process_name.lower()
    for t in targets:
        if t.process.lower() == low:
            return t
    return None
