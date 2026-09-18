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
 * 判据见 [EggTrigger]。
 */
class MjAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlay: EggOverlay? = null
    private var lastFireAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = EggOverlay(this)
        EggTrigger.onFire = { reason -> fire(reason) }
        EggTrigger.reset()
        Log.i(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val settings = SettingsStore.get(this)
        if (!settings.eggEnabled) return

        val pkg = e.packageName?.toString() ?: return
        if (pkg !in settings.enabledPackages()) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val src = e.source ?: return
                if (!isEditable(src)) return
                val text = src.text?.toString() ?: e.text?.joinToString("").orEmpty()
                EggTrigger.onInputText(text)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val src = e.source ?: return
                val label = (src.text ?: src.contentDescription)?.toString()?.trim()
                if (label != null && label in SEND_LABELS) EggTrigger.onSendClick()
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 兜底: 有些客户端不派发 TEXT_CHANGED(或只在内容变化时派发),
                // 这里仅在"已有候选命中"时才去读焦点输入框, 平时零开销。
                if (EggTrigger.hasPending()) confirmByFocusedInput()
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "无障碍服务被中断")
    }

    /** 手动播放一次(供 App 内"播放测试"按钮使用), 绕过冷却。 */
    fun testPlay() {
        mainHandler.post {
            overlay?.play() ?: Log.w(TAG, "overlay 未就绪, 无法测试播放")
        }
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
        if (instance === this) instance = null
        EggTrigger.onFire = null
        EggTrigger.reset()
        overlay?.dismiss()
        overlay = null
    }

    // ---------- 内部 ----------
    private fun confirmByFocusedInput() {
        try {
            val root = rootInActiveWindow ?: return
            val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
            if (!isEditable(focus)) return
            EggTrigger.onInputText(focus.text?.toString())
        } catch (t: Throwable) {
            Log.d(TAG, "读取焦点输入框失败: ${t.message}")
        }
    }

    private fun isEditable(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        val cls = node.className?.toString().orEmpty()
        return cls.contains("EditText", ignoreCase = true) ||
                cls.contains("RichEdit", ignoreCase = true)
    }

    private fun fire(reason: String) {
        val settings = SettingsStore.get(this)
        if (!settings.eggEnabled) return
        val now = SystemClock.elapsedRealtime()
        val cooldown = settings.cooldownSeconds.coerceIn(0, 60) * 1000L
        if (now - lastFireAt < cooldown) {
            Log.i(TAG, "冷却中, 忽略触发: $reason")
            return
        }
        lastFireAt = now
        Log.i(TAG, "触发彩蛋: $reason")
        mainHandler.post { overlay?.play() ?: Log.w(TAG, "overlay 未就绪") }
    }

    companion object {
        private const val TAG = "MjEgg.Service"

        /** 服务连接后由 onServiceConnected 写入; 未连接为 null。 */
        @Volatile
        var instance: MjAccessibilityService? = null
            private set

        /** 发送类按钮的文案(用于"点发送按钮"这条快速通道)。 */
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
