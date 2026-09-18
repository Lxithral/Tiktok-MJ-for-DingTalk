package com.lxithral.mjegg.egg

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量诊断日志 —— 手机版没有真机调试环境时, 这是唯一能看清"为什么没触发"的手段。
 *
 * 记录三类信息:
 * 1. 服务生命周期(是否连上、请求了哪些事件类型)
 * 2. 目标应用发来的事件(类型 + 读到的文本)
 * 3. 观察到的输入框节点(类名 / 是否可编辑 / 文本是否读得到) —— 每个类名只记一次
 *
 * 只在主线程写入(onAccessibilityEvent 与轮询都在主线程), 因此直接改 Compose 状态是安全的。
 */
object EggDebug {

    private const val MAX_LINES = 400

    /** 最近的日志行, 供「诊断」页直接订阅。 */
    val lines = mutableStateListOf<String>()

    /** 已观察到的输入框节点摘要(类名 -> 描述), 用于判断客户端到底暴露了什么。 */
    val inputNodes = mutableStateListOf<String>()

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val seenClasses = HashSet<String>()

    fun log(tag: String, message: String) {
        val line = "${fmt.format(Date())} [$tag] $message"
        lines.add(line)
        while (lines.size > MAX_LINES) lines.removeAt(0)
    }

    /** 记录一次输入框节点的观察结果(同类名只记第一条, 避免刷屏)。 */
    fun noteInputNode(className: String, editable: Boolean, textReadable: Boolean, text: String?) {
        val key = "$className|$editable|$textReadable"
        if (!seenClasses.add(key)) return
        val desc = "类名=$className  可编辑=$editable  文本可读=$textReadable  当前=${text?.let { "\"$it\"" } ?: "null"}"
        inputNodes.add(desc)
        while (inputNodes.size > 40) inputNodes.removeAt(0)
        log("输入框", desc)
    }

    fun clear() {
        lines.clear()
        inputNodes.clear()
        seenClasses.clear()
    }
}
