package com.lxithral.mjegg.egg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 触发词规则测试 —— 必须与桌面版 `mjegg/matcher.py` 的行为完全一致。
 * 纯 JVM 测试, 不需要真机。
 */
class MatcherTest {

    @Test
    fun `命中用例`() {
        listOf("mj", "MJ", "Mj", "mJ", "mjmj", "MJMJ", "MjMj", "mjmjmj", "MJMJMJMJ")
            .forEach { assertTrue("应命中: $it", Matcher.matches(it)) }
    }

    @Test
    fun `不命中用例`() {
        listOf("", "m", "j", "mjm", "mjx", "ajmj", "amj", "jm", "mmj", "mj mj", "中文", "mj中文")
            .forEach { assertFalse("不应命中: $it", Matcher.matches(it)) }
    }

    @Test
    fun `首尾空白被忽略`() {
        assertTrue(Matcher.matches("  mj  "))
        assertTrue(Matcher.matches("\nmjmj\t"))
    }

    @Test
    fun `奇数长度一律不命中`() {
        listOf("mjm", "mjmjm", "MJMJM").forEach {
            assertFalse("奇数字符数不应命中: $it", Matcher.matches(it))
        }
    }

    @Test
    fun `null 安全`() {
        assertFalse(Matcher.matches(null))
    }

    @Test
    fun `与桌面版同一张用例表`() {
        // 取自 README 的规则表, 两端必须一致
        val table = mapOf(
            "mj" to true, "MJ" to true, "Mj" to true,
            "mjmj" to true, "MJMJ" to true, "MjMj" to true,
            "mjm" to false, "mjx" to false, "ajmj" to false, "amj" to false,
        )
        table.forEach { (input, expected) ->
            assertEquals("规则表不一致: $input", expected, Matcher.matches(input))
        }
    }

    // ---------- 输入清洗(Android 特有: 富文本输入框可能混入不可见字符) ----------

    @Test
    fun `零宽字符被剥掉后仍能命中`() {
        // 微信/QQ 的富文本输入框可能插入 ZWSP/BOM, trim() 剥不掉它们
        assertTrue("零宽空格", Matcher.matches("mj\u200B"))
        assertTrue("BOM", Matcher.matches("\uFEFFmj"))
        assertTrue("中间零宽", Matcher.matches("m\u200Bj"))
        assertTrue("不换行空格", Matcher.matches("mj\u00A0"))
        assertTrue("词连接符", Matcher.matches("mj\u2060"))
        assertTrue("零宽连接符", Matcher.matches("mj\u200D"))
    }

    @Test
    fun `剥掉不可见字符后仍然不放过非法组合`() {
        assertFalse(Matcher.matches("amj\u200B"))
        assertFalse(Matcher.matches("mjm\uFEFF"))
        assertFalse(Matcher.matches("mjx\u00A0"))
    }

    @Test
    fun `归一化只去不可见字符, 不动可见内容`() {
        assertEquals("mj", Matcher.normalize("  mj\u200B  "))
        assertEquals("mj mj", Matcher.normalize("mj mj"))
        assertEquals("", Matcher.normalize("\u200B\uFEFF"))
        assertEquals("", Matcher.normalize(null))
    }

    @Test
    fun `归一化不会把带空格的输入变成命中`() {
        // 插入空格属于"内容变了", 与桌面版行为一致: 不命中
        assertFalse(Matcher.matches("mj mj"))
        assertFalse(Matcher.matches("m j"))
    }
}
