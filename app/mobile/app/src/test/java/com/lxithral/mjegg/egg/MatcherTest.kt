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
}
