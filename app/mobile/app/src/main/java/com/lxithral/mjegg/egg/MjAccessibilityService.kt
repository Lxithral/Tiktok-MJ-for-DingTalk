package com.lxithral.mjegg.egg

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    /** 最近一次收到事件的目标包名, 用于把统计归到正确的应用上。 */
    private var lastTargetPkg: String? = null

    /** 诊断广播接收器(见 registerDebugReceiver)。 */
    private var debugReceiver: BroadcastReceiver? = null

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
        registerDebugReceiver()
        EggDebug.log("服务", "已连接; 请求的事件类型=${serviceInfo?.eventTypes?.let { "0x%X".format(it) } ?: "?"}")
        Log.i(TAG, "无障碍服务已连接")
    }

    /**
     * 诊断用广播接收器。
     *
     * 为什么需要它: 导出控件树必须在**目标应用仍在前台**时执行, 否则
     * `rootInActiveWindow` 读到的是我们自己的窗口。用广播触发就不用切前台了:
     *
     *   adb shell am broadcast -a com.lxithral.mjegg.DUMP_TREE
     */
    private fun registerDebugReceiver() {
        if (debugReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_DUMP_TREE -> {
                        dumpActiveWindowTree()
                        dumpAllWindowsInfo()
                    }
                    ACTION_CLEAR_LOG -> EggDebug.clear()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_DUMP_TREE)
            addAction(ACTION_CLEAR_LOG)
        }
        // ADB(shell) 要能发过来, 必须导出
        registerReceiver(r, filter, RECEIVER_EXPORTED)
        debugReceiver = r
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
        debugReceiver?.let {
            runCatching { unregisterReceiver(it) }
        }
        debugReceiver = null
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
        lastTargetPkg = pkg
        EggDebug.noteEvent(pkg)

        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> onTextChanged(e, pkg)

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val label = labelOf(e)
                if (label != null && label in SEND_LABELS) {
                    EggDebug.log("事件", "$pkg 点击了发送按钮 \"$label\"")
                    trigger.onSendClick()
                }
            }

            // 微信实测(2026-09 真机抓包): 发送后清空输入框, 微信派发的是"光标变化"事件,
            // 且此时它的窗口树对无障碍**完全不可见**(rootInActiveWindow 只有 1 个根节点,
            // uiautomator dump 同样是空树) —— 所以"轮询读节点确认变空"这条路在微信上永远读不到。
            // 唯一可用的信号是**事件自带的 text 列表**: 打字时是 ["mj"], 清空时是 [](空列表)。
            // 因此光标变化事件与 TEXT_CHANGED 走完全相同的处理: 非空列表当文本喂状态机;
            // 空列表 + 已有候选 => 按"输入框被清空"确认发送(onTextChanged 内部会先尝试读
            // source 节点文本, 读得到非空就不会误触发)。
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> onTextChanged(e, pkg)

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
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

        // 钉钉实测(2026-09-26 真机抓包): 清空输入框派发的光标事件, text 列表里装的是
        // **占位 hint**(聊天框是"记录一下"), 不是用户文本也不是空列表(微信才是空列表)。
        // 直接喂状态机会把"记录一下"当输入 -> 撤销刚登记的 mj 候选, 触发永远出不来。
        // 防御: 事件文本 == 节点自己的 hintText => 实际语义是"输入框已空"。
        // 从节点现读 hint 而不是写死提示语, 提示语随场景变(单聊/群聊)也不怕。
        val hint = runCatching { src?.hintText?.toString() }.getOrNull()
        val eventIsHint = fromEvent != null && hint != null && fromEvent == hint
        val effectiveEvent = if (eventIsHint) null else fromEvent

        // 特例(微信实测): 客户端**清空输入框**时发的就是一个"空列表"事件。
        // 空列表平时不能当"文本为空"(否则每条事件都会误判成清空), 但**当已有候选命中时**,
        // 它只可能是"输入框被清空" —— 这恰恰就是我们要的发送信号。
        // 钉钉的"事件文本==hint"同样只在有候选时按清空处理。
        val clearedByEmptyList = fromSource == null && effectiveEvent == null &&
                (e.text.isEmpty() || eventIsHint) && trigger.hasPending()

        val text = fromSource ?: effectiveEvent ?: if (clearedByEmptyList) "" else null
        val normalized = text?.let { Matcher.normalize(it) }
        EggDebug.noteTextChanged(pkg, text != null, normalized)
        EggDebug.log(
            "文本变化",
            "$pkg source=${if (src == null) "null" else src.className} 可编辑=$srcEditable " +
                    "source文本=${EggDebug.escape(fromSource)} event文本=${EggDebug.escape(fromEvent)} " +
                    "hint=${EggDebug.escape(hint)} hint即文本=$eventIsHint " +
                    "空列表清空=$clearedByEmptyList 归一化后=${EggDebug.escape(normalized)}"
        )

        if (text != null) {
            if (Matcher.matches(text)) EggDebug.noteMatch(pkg)
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
     * **实测教训(微信)**: 微信聊天输入框在无障碍里的 `className` 是 null、`isEditable` 是 false。
     * 早期版本先判断"可编辑"再读文本, 结果直接跳过、连文本都没读 —— 而它恰恰就是
     * `FOCUS_INPUT`。所以这里改成**只要能读到文本就用**, 不再要求节点"可编辑":
     * `findFocus(FOCUS_INPUT)` 本身已经足够说明它是输入焦点所在。
     *
     * 唯一的硬约束: 文本读到 null 就不上报(既不当成空串, 也不撤销候选) ——
     * 否则每次轮询都会误判成"输入框被清空"。
     */
    private fun pollFocusedInput(reason: String) {
        val focus = focusedInputNode() ?: return
        var node = focus
        var cls = node.className?.toString()
        var raw = node.text?.toString()
        // 微信实测: findFocus(FOCUS_INPUT) 返回的是 ChattingUILayout 这种**容器**,
        // 它自己没有 className/text; 真正的输入框在它的子树里。所以要往下找一层。
        if (raw == null) {
            val inner = findInputInSubtree(focus, 0)
            if (inner != null) {
                node = inner
                cls = inner.className?.toString()
                raw = inner.text?.toString()
            }
        }
        val nodePkg = try {
            node.packageName?.toString()
        } catch (t: Throwable) {
            null
        }
        val editable = isEditableNode(node)
        EggDebug.noteInputNode(cls ?: "?", editable, raw != null, raw)
        if (raw == null) return
        val normalized = Matcher.normalize(raw)
        EggDebug.noteInputRead(nodePkg ?: lastTargetPkg, cls, true, normalized)
        if (Matcher.matches(raw)) EggDebug.noteMatch(nodePkg ?: lastTargetPkg)
        trigger.onInputText(raw)
        // 注意: 这里**不要**再调 ensurePendingPolling —— 轮询链的续期由 pendingPoller
        // 自己的尾部负责, 两处都排程会让同一时刻存在两条链, 实际频率翻倍。
        if (reason == "轮询" && raw.isNotEmpty()) {
            EggDebug.log("轮询", "焦点输入框=$reason 文本=${EggDebug.escape(raw)} 归一化后=${EggDebug.escape(normalized)}")
        }
    }

    /**
     * 在子树里找"像输入框"的节点。
     * 判据: 可编辑 / 类名含 Edit / 有输入焦点 / 有 hint —— 只往下钻有限层, 避免代价失控。
     */
    private fun findInputInSubtree(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 6) return null
        val n = node.childCount
        for (i in 0 until n) {
            val c = try {
                node.getChild(i)
            } catch (t: Throwable) {
                null
            } ?: continue
            val cc = c.className?.toString().orEmpty()
            val looksInput = c.isEditable || c.isFocused ||
                    cc.contains("Edit", true) ||
                    (try {
                        c.hintText != null
                    } catch (t: Throwable) {
                        false
                    })
            if (looksInput) return c
            findInputInSubtree(c, depth + 1)?.let { return it }
        }
        return null
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
        EggDebug.noteEmpty(lastTargetPkg)
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

    /**
     * 诊断用: 把当前活动窗口的控件树导出到日志。
     *
     * 只记录"有文本 / 有描述 / 可编辑 / 有输入焦点"的节点 —— 目的是看清目标客户端
     * 到底把什么暴露给了无障碍, 而不是盲猜。排查"读不到输入框文本"时先跑这个。
     */
    fun dumpActiveWindowTree(): String {
        val root = rootInActiveWindow ?: run {
            EggDebug.logTree("rootInActiveWindow = null (当前没有活动窗口)")
            return "没有活动窗口"
        }
        val lines = ArrayList<String>()
        var total = 0
        var kept = 0

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 30 || kept >= 150) return
            total++
            val cls = node.className?.toString() ?: "?"
            val txt = try {
                node.text?.toString()
            } catch (t: Throwable) {
                null
            }
            val cd = try {
                node.contentDescription?.toString()
            } catch (t: Throwable) {
                null
            }
            val hint = try {
                node.hintText?.toString()
            } catch (t: Throwable) {
                null
            }
            val interesting = txt != null || cd != null || hint != null ||
                    node.isEditable || node.isFocused || cls.contains("Edit", true)
            if (interesting) {
                kept++
                val sb = StringBuilder()
                repeat(depth.coerceAtMost(10)) { sb.append("  ") }
                sb.append(cls)
                sb.append(" editable=").append(node.isEditable)
                sb.append(" focused=").append(node.isFocused)
                sb.append(" text=").append(EggDebug.escape(txt))
                if (cd != null) sb.append(" desc=").append(EggDebug.escape(cd))
                if (hint != null) sb.append(" hint=").append(EggDebug.escape(hint))
                lines.add(sb.toString())
            }
            val n = node.childCount
            for (i in 0 until n) {
                walk(try {
                    node.getChild(i)
                } catch (t: Throwable) {
                    null
                }, depth + 1)
            }
        }

        walk(root, 0)
        val head = "root=${root.packageName} 遍历 $total 个节点, 记录 $kept 条"
        EggDebug.log("控件树", head)
        lines.forEach { EggDebug.logTree(it) }
        return head
    }

    /**
     * 诊断用: 枚举所有窗口, 打到 logcat。
     *
     * `dumpActiveWindowTree` 只看 rootInActiveWindow; 但有的客户端(微信实测)会把
     * 活动窗口的子树整个藏掉, 而别的窗口可能可见, 也可能是"藏掉的是活动窗口本身"。
     * 这个方法把每个窗口的元数据 + 节点数 + 前几个"像输入框"的节点都打出来,
     * 用于判断"树不可见"到底是客户端的哪个窗口在作怪。
     */
    fun dumpAllWindowsInfo() {
        val ws = runCatching { windows }.getOrNull()
        if (ws == null) {
            Log.i(TAG, "win: windows 属性不可用")
            return
        }
        Log.i(TAG, "win: 共 ${ws.size} 个窗口")
        for (w in ws) {
            val root = runCatching { w.root }.getOrNull()
            if (root == null) {
                Log.i(TAG, "win id=${w.id} type=${w.type} title=${w.title} active=${w.isActive} focused=${w.isFocused} root=null")
                continue
            }
            var total = 0
            var kept = 0
            val samples = ArrayList<String>()
            fun walk(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null || kept >= 8) return
                total++
                val cls = node.className?.toString() ?: "?"
                val txt = runCatching { node.text?.toString() }.getOrNull()
                if (node.isEditable || node.isFocused || cls.contains("Edit", true) || txt != null) {
                    kept++
                    if (samples.size < 8) {
                        samples.add("    cls=$cls editable=${node.isEditable} focused=${node.isFocused} text=${EggDebug.escape(txt)}")
                    }
                }
                val n = node.childCount
                for (i in 0 until n) {
                    walk(runCatching { node.getChild(i) }.getOrNull(), depth + 1)
                }
            }
            walk(root, 0)
            Log.i(TAG, "win id=${w.id} type=${w.type} title=${w.title} active=${w.isActive} focused=${w.isFocused} rootPkg=${root.packageName} 遍历=$total 记录=$kept")
            samples.forEach { Log.i(TAG, it) }
        }
    }

    companion object {
        private const val TAG = "MjEgg.Service"

        /** 诊断广播: 导出当前窗口控件树 / 清空日志(见 registerDebugReceiver)。 */
        const val ACTION_DUMP_TREE = "com.lxithral.mjegg.DUMP_TREE"
        const val ACTION_CLEAR_LOG = "com.lxithral.mjegg.CLEAR_LOG"

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
