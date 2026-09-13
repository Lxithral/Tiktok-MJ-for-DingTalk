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
    """键盘 VK 缓冲兜底判定(缓冲内为大写字母). 检查尾部 2/4/6/8 位是否为 mj 组合."""
    s = "".join(buf).lower()
    for n in (2, 4, 6, 8):
        if len(s) >= n and matches_trigger(s[-n:]):
            return True
    return False
