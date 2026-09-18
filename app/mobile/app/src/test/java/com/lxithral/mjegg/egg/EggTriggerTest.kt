package com.lxithral.mjegg.egg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 触发状态机测试 —— 这是手机版最核心的逻辑, 用可注入时钟在 JVM 上完整验证。
 *
 * 覆盖: 正常发送 / 退格删字不误触发 / 命中后超时不触发 /
 *      点发送按钮快速通道 / 连续两次发送 / 冷却由上层负责。
 */
class EggTriggerTest {

    /** 可控时钟 */
    private var now = 1_000L
    private lateinit var trigger: EggTrigger
    private val fired = mutableListOf<String>()

    private fun newTrigger(window: Long = EggTrigger.DEFAULT_MATCH_WINDOW_MS): EggTrigger {
        fired.clear()
        return EggTrigger(clock = { now }, matchWindowMs = window)
            .also { it.onFire = { reason -> fired.add(reason) } }
    }

    private fun advance(ms: Long) {
        now += ms
    }

    @Test
    fun `输入 mj 后清空则触发`() {
        trigger = newTrigger()
        trigger.onInputText("mj")
        assertTrue(trigger.hasPending())
        trigger.onInputText("")
        assertEquals(1, fired.size)
        assertFalse("触发后候选应被清空", trigger.hasPending())
    }

    @Test
    fun `逐字符输入 mj 再清空则触发`() {
        trigger = newTrigger()
        trigger.onInputText("m")
        assertFalse("只输入 m 不应成为候选", trigger.hasPending())
        trigger.onInputText("mj")
        assertTrue(trigger.hasPending())
        trigger.onInputText("")
        assertEquals(1, fired.size)
    }

    @Test
    fun `退格删字清空则不触发`() {
        trigger = newTrigger()
        trigger.onInputText("mj")
        assertTrue(trigger.hasPending())
        // 退格: mj → m → 空, 中途文本变成非触发词, 候选应被撤销
        trigger.onInputText("m")
        assertFalse(trigger.hasPending())
        trigger.onInputText("")
        assertEquals("退格删字不应触发", 0, fired.size)
    }

    @Test
    fun `全选删除(直接变空但内容不是触发词)则不触发`() {
        trigger = newTrigger()
        trigger.onInputText("hello")
        trigger.onInputText("")
        assertEquals(0, fired.size)
    }

    @Test
    fun `命中后超过时间窗才清空则不触发`() {
        trigger = newTrigger(window = 1_000L)
        trigger.onInputText("mj")
        advance(1_500L)
        trigger.onInputText("")
        assertEquals("超出时间窗不应触发", 0, fired.size)
        assertFalse("超时后候选应被丢弃", trigger.hasPending())
    }

    @Test
    fun `命中后正好在时间窗内清空则触发`() {
        trigger = newTrigger(window = 1_000L)
        trigger.onInputText("mj")
        advance(1_000L)
        trigger.onInputText("")
        assertEquals(1, fired.size)
    }

    @Test
    fun `空输入框反复上报则不触发`() {
        trigger = newTrigger()
        repeat(5) { trigger.onInputText("") }
        assertEquals(0, fired.size)
    }

    @Test
    fun `mjmj 也触发`() {
        trigger = newTrigger()
        trigger.onInputText("mjmj")
        trigger.onInputText("")
        assertEquals(1, fired.size)
    }

    @Test
    fun `点发送按钮立即触发, 不必等清空`() {
        trigger = newTrigger()
        trigger.onInputText("mj")
        trigger.onSendClick()
        assertEquals(1, fired.size)
        assertFalse(trigger.hasPending())
    }

    @Test
    fun `没有候选时点发送按钮则不触发`() {
        trigger = newTrigger()
        trigger.onSendClick()
        assertEquals(0, fired.size)
    }

    @Test
    fun `连续两次发送各自触发`() {
        trigger = newTrigger()
        trigger.onInputText("mj")
        trigger.onInputText("")
        advance(5_000L)
        trigger.onInputText("mjmj")
        trigger.onInputText("")
        assertEquals(2, fired.size)
    }

    @Test
    fun `reset 清掉候选`() {
        trigger = newTrigger()
        trigger.onInputText("mj")
        trigger.reset()
        assertFalse(trigger.hasPending())
        trigger.onInputText("")
        assertEquals(0, fired.size)
    }

    @Test
    fun `清空后不重复触发`() {
        trigger = newTrigger()
        trigger.onInputText("mj")
        trigger.onInputText("")
        trigger.onInputText("")
        trigger.onInputText("")
        assertEquals("空输入框重复上报只应触发一次", 1, fired.size)
    }
}
