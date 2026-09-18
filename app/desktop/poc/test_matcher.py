# -*- coding: utf-8 -*-
"""matcher 单元测试: 移植自原项目 MJMatches 规则."""
import sys
sys.path.insert(0, "..")
from mjegg.matcher import matches_trigger, matches_vk_buffer

cases = {
    "mj": True, "MJ": True, "Mj": True, "mJ": True,
    "mjmj": True, "MJMJ": True, "MjMj": True,
    "mjx": False, "mjm": False, "ajmj": False, "m": False, "": False,
    " mm": False, "mj ": True,  # strip 后命中
    "mjmjmj": True, "mjmjm": False,
}
bad = 0
for t, expect in cases.items():
    got = matches_trigger(t)
    if got != expect:
        bad += 1
        print(f"FAIL matches_trigger({t!r}) = {got}, expect {expect}")

vk_cases = [
    (list("MJ"), True), (list("mjmj"), True), (list("HELLMJ"), True),
    (list("mjm"), False), (list("x"), False), ([], False), (list("M"), False),
]
for buf, expect in vk_cases:
    got = matches_vk_buffer(buf)
    if got != expect:
        bad += 1
        print(f"FAIL matches_vk_buffer({buf}) = {got}, expect {expect}")

print("matcher 测试:", "全部通过" if bad == 0 else f"{bad} 个失败")
sys.exit(1 if bad else 0)
