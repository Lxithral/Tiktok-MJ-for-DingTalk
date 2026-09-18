# -*- coding: utf-8 -*-
"""触发词匹配: 移植原项目 MJMatches 规则.
长度 >= 2 且为偶数, 每 2 个字符一组均为 "mj"(忽略大小写):
  命中: mj / MJ / mjmj / MjMj / mjmjmjmj ...
  不中: mjm / mjx / ajmj / 空串 ...
"""


def matches_trigger(text: str) -> bool:
    t = (text or "").strip()
    if len(t) < 2 or len(t) % 2 != 0:
        return False
    for i in range(0, len(t), 2):
        if t[i:i + 2].lower() != "mj":
            return False
    return True


def matches_vk_buffer(buf) -> bool:
    """键盘 VK 缓冲兜底判定(缓冲内为大写字母).

    缓冲的语义 = "从上次发送之后, 被敲进输入框的全部内容", 所以这里做**整串**匹配,
    不做尾部模糊匹配 —— 否则 `amj`/`ajmj` 会因为结尾是 "MJ" 而被误判触发,
    与原项目"ajmj 不触发"的规则冲突.
    """
    return matches_trigger("".join(buf))
