package com.lxithral.mjegg.egg

import android.os.SystemClock

/**
 * 触发状态机 —— 与桌面版 `mjegg/watcher.py` 的判据保持一致:
 *
 *   命中触发词(输入框里出现 mj/mjmj) + 输入框随后被清空  ⇒  判定"用户发送了消息"
 *
 * 为什么用"清空"当发送信号: 这是唯一不依赖客户端实现细节的可靠信号 ——
 * 无论用户是按回车发送、点发送按钮, 还是用输入法的发送键, 消息发出后输入框都会空掉。
 * 反过来, 只有"命中过触发词"且清空发生在时间窗内才算, 手动退格删字不会误触发
 * (退格过程中文本会先变成非触发词, 候选随即被清掉)。
 *
 * 时钟做成可注入的, 这样这套判定逻辑可以在 JVM 单元测试里被完整验证
 * (见 `src/test/java/.../EggTriggerTest.kt`)。
 */
class EggTrigger(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val matchWindowMs: Long = DEFAULT_MATCH_WINDOW_MS,
) {

    private var lastMatchText: String? = null
    private var lastMatchAt = 0L

    /** 判定成立时的回调; 参数是触发原因(用于日志)。 */
    var onFire: ((String) -> Unit)? = null

    /** 是否已有候选命中(用于决定要不要做代价更高的兜底读取)。 */
    fun hasPending(): Boolean = lastMatchText != null

    fun reset() {
        lastMatchText = null
        lastMatchAt = 0L
    }

    /** 输入框文本变化时调用。 */
    fun onInputText(text: String?) {
        val now = clock()
        val t = text?.trim().orEmpty()
        if (t.isNotEmpty()) {
            if (Matcher.matches(t)) {
                lastMatchText = t
                lastMatchAt = now
            } else {
                // 内容变成了非触发词(例如退格删掉一个字符) -> 撤销候选
                lastMatchText = null
                lastMatchAt = 0L
            }
            return
        }
        // 输入框变空
        val matched = lastMatchText ?: return
        if (now - lastMatchAt <= matchWindowMs) {
            lastMatchText = null
            lastMatchAt = 0L
            onFire?.invoke("输入框清空(命中 $matched)")
        } else {
            // 候选已过期: 必须丢弃, 否则 hasPending() 会永远为真,
            // 上层就会对每个内容变化事件都去做一次昂贵的焦点输入框读取
            lastMatchText = null
            lastMatchAt = 0L
        }
    }

    /** 点了"发送"按钮: 立刻确认候选, 不必等输入框清空。 */
    fun onSendClick() {
        val now = clock()
        val matched = lastMatchText ?: return
        if (now - lastMatchAt <= matchWindowMs) {
            lastMatchText = null
            lastMatchAt = 0L
            onFire?.invoke("点击发送按钮(命中 $matched)")
        } else {
            lastMatchText = null
            lastMatchAt = 0L
        }
    }

    companion object {
        /** 命中触发词后, 多久之内清空才算"发送"(毫秒)。 */
        const val DEFAULT_MATCH_WINDOW_MS = 8_000L
    }
}
