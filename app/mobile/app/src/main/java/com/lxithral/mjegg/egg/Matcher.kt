package com.lxithral.mjegg.egg

/**
 * 触发词匹配 —— 与桌面版 `mjegg/matcher.py` 规则完全一致:
 * 长度 >= 2 且为偶数, 每 2 个字符一组均为 "mj"(忽略大小写)。
 *
 *   命中: mj / MJ / mjmj / MjMj / mjmjmjmj ...
 *   不中: mjm / mjx / ajmj / 空串 ...
 */
object Matcher {

    fun matches(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.length < 2 || t.length % 2 != 0) return false
        var i = 0
        while (i < t.length) {
            val pair = t.substring(i, i + 2)
            if (!pair.equals("mj", ignoreCase = true)) return false
            i += 2
        }
        return true
    }
}
