package com.lxithral.mjegg.egg

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lxithral.mjegg.platform.SettingsStore

/**
 * MJ 彩蛋无障碍服务。
 *
 * 职责: 只读监听 微信 / QQ / 钉钉 的聊天输入框文本, 判断"用户自己发送了 mj",
 * 然后播一遍蜘蛛侠动画。**不注入按键、不模拟点击、不自动发消息**。
 *
 * ## 判据
 *
 *   输入框命中触发词 + 输入框随后变空  ⇒  判定用户发送了消息
 *
 * 这条判据**不关心用户是怎么发的** —— 点客户端自带的发送按钮、按输入法的发送键、
 * 或者按物理回车, 消息发出后输入框都会空掉。所以不需要单独去识别"发送按钮"。
 *
 * ## 为什么必须"事件 + 轮询"两条腿走路
 *
 * 1. `TYPE_VIEW_TEXT_CHANGED` 的 `event.source` **在很多机型/客户端上是 null**
 *    (早期版本直接 `source ?: return`, 结果三个客户端全都不触发 —— 实测踩坑)。
 *    所以文本来源按 `source.text` → `event.text` → 焦点输入框 三级回退。
 * 2. `event.text` 是 List: **空列表表示"客户端没填", 不能当成空字符串**,
 *    否则每次事件都会被误判成"输入框已清空"。
 * 3. 有些客户端根本不在清空输入框时派发事件, 所以命中候选后要**主动轮询**
 *    焦点输入框(与电脑版 60ms 轮询同一个思路), 靠它观测"被清空"这一瞬间。
 */
class MjAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val trigger = EggTrigger()
    private var overlay: EggOverlay? = null
    private var lastFireAt = 0L
    private var lastPollAt = 0L

    /**
     * 命中候选后开启的高频轮询。
     * 只在候选有效期内运行(通常不到一两秒), 之后自动停, 不做无谓开销。
     *
     * 调度只由 [ensurePendingPolling] 和本 Runnable 的尾部两处负责, 二者都先
     * removeCallbacks 再 post, 保证同一时刻只有一条待执行的链, 不会翻倍跑。
     */
    private val pendingPoller = object : Runnable {
        override fun run() {
            if (!trigger.hasPending()) return
            pollFocusedInput("轮询")
            if (trigger.hasPending()) mainHandler.postDelayed(this, PENDING_POLL_MS)
        }
    }

    /** 确保轮询链在跑(幂等)。只在事件回调里调用, 不要在 pollFocusedInput 里调, 否则会重复排程。 */
    private fun ensurePendingPolling() {
        if (!trigger.hasPending()) return
        mainHandler.removeCallbacks(pendingPoller)
        mainHandler.postDelayed(pendingPoller, PENDING_POLL_MS)
    }

    // ---------- 生命周期 ----------
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = EggOverlay(this)
        trigger.onFire = { reason -> fire(reason) }
        trigger.reset()
        EggDebug.log("服务", "已连接; 请求的事件类型=${serviceInfo?.eventTypes?.let { "0x%X".format(it) } ?: "?"}")
        Log.i(TAG, "无障碍服务已连接")
    }

    override fun onInterrupt() {
        EggDebug.log("服务", "被中断")
        Log.i(TAG, "无障碍服务被中断")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        mainHandler.removeCallbacks(pendingPoller)
        if (instance === this) instance = null
        trigger.onFire = null
        trigger.reset()
        overlay?.dismiss()
        overlay = null
    }

    // ---------- 事件 ----------
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val settings = SettingsStore.get(this)
        if (!settings.eggEnabled) return

        val pkg = e.packageName?.toString() ?: return
        if (pkg !in settings.enabledPackages()) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> onTextChanged(e, pkg)

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val label = labelOf(e)
                if (label != null && label in SEND_LABELS) {
                    EggDebug.log("事件", "$pkg 点击了发送按钮 \"$label\"")
                    trigger.onSendClick()
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 没有候选时也低频读一次: 兜住"清空输入框不派发 TEXT_CHANGED"的客户端
                pollFocusedInputThrottled("内容/窗口变化")
            }
        }
    }

    private fun onTextChanged(e: AccessibilityEvent, pkg: String) {
        val src = safeSource(e)
        val srcEditable = src != null && isEditableNode(src)
        val fromSource = if (srcEditable) src?.text?.toString() else null
        // 空列表 = 客户端没填, 不能当空串; 非空列表才可用。
        // filterNotNull: 列表里理论上可能出现 null 元素, 不过滤的话 joinToString 会
        // 拼出字面量 "null", 把文本污染成 "nullmj" 之类。
        val fromEvent = if (e.text.isNotEmpty()) e.text.filterNotNull().joinToString("") else null

        val text = fromSource ?: fromEvent
        EggDebug.log(
            "文本变化",
            "$pkg source=${if (src == null) "null" else src.className} 可编辑=$srcEditable " +
                    "source文本=${EggDebug.escape(fromSource)} event文本=${EggDebug.escape(fromEvent)} " +
                    "归一化后=${EggDebug.escape(text?.let { Matcher.normalize(it) })}"
        )

        if (text != null) {
            trigger.onInputText(text)
            if (trigger.hasPending()) ensurePendingPolling()
        } else {
            // 事件里读不到文本: 直接去读焦点输入框
            pollFocusedInput("TEXT_CHANGED 兜底")
        }
    }

    // ---------- 读取输入框 ----------
    private fun pollFocusedInputThrottled(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPollAt < IDLE_POLL_THROTTLE_MS) return
        lastPollAt = now
        pollFocusedInput(reason)
    }

    /**
     * 读当前焦点输入框并喂给状态机。
     *
     * 两条安全约束:
     * - 读不到文本(null)且当前没有候选 -> 直接忽略。**绝不能把 null 当成"空字符串"**,
     *   否则每次轮询都会误判成"输入框被清空"。
     * - 焦点节点不是可编辑控件时不上报(例如焦点跑到列表上), 避免误判。
     */
    private fun pollFocusedInput(reason: String) {
        val focus = focusedInputNode() ?: return
        if (!isEditableNode(focus)) {
            EggDebug.noteInputNode(focus.className?.toString() ?: "?", false, false, null)
            return
        }
        val raw = focus.text?.toString()
        EggDebug.noteInputNode(focus.className?.toString() ?: "?", true, raw != null, raw)
        if (raw == null && !trigger.hasPending()) return
        trigger.onInputText(raw ?: "")
        // 注意: 这里**不要**再调 ensurePendingPolling —— 轮询链的续期由 pendingPoller
        // 自己的尾部负责, 两处都排程会让同一时刻存在两条链, 实际频率翻倍。
        if (reason == "轮询" && raw != null && raw.isNotEmpty()) {
            EggDebug.log("轮询", "焦点输入框=$reason 文本=${EggDebug.escape(raw)} 归一化后=${EggDebug.escape(Matcher.normalize(raw))}")
        }
    }

    private fun focusedInputNode(): AccessibilityNodeInfo? = try {
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    } catch (t: Throwable) {
        Log.d(TAG, "读取焦点输入框失败: ${t.message}")
        null
    }

    private fun safeSource(e: AccessibilityEvent): AccessibilityNodeInfo? = try {
        e.source
    } catch (t: Throwable) {
        null
    }

    private fun labelOf(e: AccessibilityEvent): String? {
        val src = safeSource(e)
        val raw = src?.text ?: src?.contentDescription
        return raw?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * 判断是否"可编辑的输入框"。
     *
     * 只靠 `isEditable` 不够 —— 不少客户端自定义 EditText 时不会设置这个属性,
     * 所以再加两条兜底: 类名含 EditText / RichEdit, 或者节点支持 ACTION_SET_TEXT。
     */
    private fun isEditableNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        val cls = node.className?.toString().orEmpty()
        if (cls.contains("EditText", ignoreCase = true) ||
            cls.contains("RichEdit", ignoreCase = true) ||
            cls.contains("EditText", ignoreCase = true)
        ) {
            return true
        }
        val actions = node.actionList ?: return false
        return actions.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
    }

    // ---------- 触发 ----------
    private fun fire(reason: String) {
        val settings = SettingsStore.get(this)
        if (!settings.eggEnabled) return
        val now = SystemClock.elapsedRealtime()
        val cooldown = settings.cooldownSeconds.coerceIn(0, 60) * 1000L
        if (now - lastFireAt < cooldown) {
            EggDebug.log("触发", "冷却中, 忽略: $reason")
            return
        }
        lastFireAt = now
        EggDebug.log("触发", reason)
        Log.i(TAG, "触发彩蛋: $reason")
        mainHandler.post {
            val ok = overlay?.play() ?: false
            if (!ok) EggDebug.log("触发", "播放失败(overlay 未就绪或素材解码失败)")
        }
    }

    /** 手动播放一次(供 App 内"播放测试"按钮使用), 绕过冷却。 */
    fun testPlay() {
        EggDebug.log("测试", "手动播放")
        mainHandler.post {
            val ok = overlay?.play() ?: false
            if (!ok) EggDebug.log("测试", "播放失败(overlay 未就绪或素材解码失败)")
        }
    }

    companion object {
        private const val TAG = "MjEgg.Service"

        /** 候选有效期内的高频轮询间隔(观测"输入框被清空"这一瞬间)。 */
        private const val PENDING_POLL_MS = 80L

        /** 无候选时的低频轮询节流(兜住不派发 TEXT_CHANGED 的客户端)。 */
        private const val IDLE_POLL_THROTTLE_MS = 150L

        /** 服务连接后由 onServiceConnected 写入; 未连接为 null。 */
        @Volatile
        var instance: MjAccessibilityService? = null
            private set

        /** 发送类按钮的文案(仅作为"点发送按钮"这条快速通道, 不是必须的)。 */
        private val SEND_LABELS = setOf("发送", "Send", "send", "发送消息")

        /** 服务是否真的处于连接状态(比查设置更准)。 */
        fun isConnected(): Boolean = instance != null

        /** 系统设置里是否已勾选本服务。 */
        fun isEnabledInSettings(context: Context): Boolean {
            val expected = ComponentName(context, MjAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
