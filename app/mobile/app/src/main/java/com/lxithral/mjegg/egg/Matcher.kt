package com.lxithral.mjegg.egg

/**
 * 触发词匹配 —— 规则与桌面版 `mjegg/matcher.py` 完全一致:
 * 长度 >= 2 且为偶数, 每 2 个字符一组均为 "mj"(忽略大小写)。
 *
 *   命中: mj / MJ / mjmj / MjMj / mjmjmj ...
 *   不中: mjm / mjx / ajmj / 空串 ...
 *
 * 与桌面版的唯一差别在**输入清洗**: Android 的富文本输入框(微信/QQ 的聊天框都是)
 * 可能在文本里混入零宽字符、BOM、不换行空格这类**看不见**的字符, 而 `trim()` 并不
 * 覆盖它们(例如 NBSP 按 `Char.isWhitespace()` 判断为 false)。这类字符会让
 * "看起来是 mj" 的文本匹配失败, 所以这里先剥掉再匹配。
 * 触发词本身的规则两边是同一套, 没有放宽。
 */
object Matcher {

    /** 零宽字符 / BOM / 不换行空格 / 词连接符 等不可见字符 */
    private val INVISIBLE = Regex("[\\u200B-\\u200F\\u2028\\u2029\\u2060\\uFEFF\\u00A0]")

    /** 去掉不可见字符并 trim。诊断日志里也用同一套清洗结果做对比。 */
    fun normalize(text: String?): String =
        INVISIBLE.replace(text.orEmpty(), "").trim()

    fun matches(text: String?): Boolean {
        val t = normalize(text)
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
