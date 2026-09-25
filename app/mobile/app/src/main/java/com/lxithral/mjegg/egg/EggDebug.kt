package com.lxithral.mjegg.egg

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量诊断 —— 手机版没有真机调试环境时, 这是唯一能看清"为什么没触发"的手段。
 *
 * 除了流水日志, 还会按目标应用累计统计, 并据此给出**结论**:
 * 是"服务根本没连上"、"读不到输入框文本", 还是"判据没走通"。
 * 三种情况要采取的行动完全不同, 所以不能只丢一堆原始日志让人猜。
 *
 * 只在主线程写入(onAccessibilityEvent 与轮询都在主线程), 因此直接改 Compose 状态是安全的。
 */
object EggDebug {

    private const val MAX_LINES = 400

    /** 最近的日志行, 供「诊断」页直接订阅。 */
    val lines = mutableStateListOf<String>()

    /** 已观察到的输入框节点摘要(类名 -> 描述), 用于判断客户端到底暴露了什么。 */
    val inputNodes = mutableStateListOf<String>()

    /** 控件树导出的结果(单独一块, 避免把事件流水冲掉)。 */
    val treeLines = mutableStateListOf<String>()

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val seenClasses = HashSet<String>()

    /** 按包名累计的统计。非 Compose 状态, 由「诊断」页定时取快照。 */
    private val stats = LinkedHashMap<String, PkgStat>()

    class PkgStat {
        var events = 0
        var textChanged = 0
        var textUnreadable = 0
        var matches = 0
        var empties = 0
        var inputSeen = false
        var inputTextReadable = false
        var inputClass: String? = null
        var lastText: String? = null
    }

    // ---------- 日志 ----------
    fun log(tag: String, message: String) {
        val line = "${fmt.format(Date())} [$tag] $message"
        lines.add(line)
        while (lines.size > MAX_LINES) lines.removeAt(0)
    }

    /** 控件树导出专用(单独一块, 不会被事件流水冲掉)。 */
    fun logTree(line: String) {
        treeLines.add(line)
        while (treeLines.size > 200) treeLines.removeAt(0)
    }

    /**
     * 把文本转成"看得见"的形式写进日志。
     *
     * 关键作用: 把不可见字符(零宽字符/BOM/不换行空格/控制符)转义成 `\uXXXX`。
     * 否则日志里 `"mj"` 和 `"mj\u200B"` 长得一模一样, 根本查不出为什么匹配失败。
     */
    fun escape(s: String?): String {
        if (s == null) return "null"
        val sb = StringBuilder("\"")
        for (ch in s) {
            val c = ch.code
            when {
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                c < 0x20 || c == 0x7F ||
                        c in 0x200B..0x200F || c == 0x2060 || c == 0xFEFF || c == 0x00A0 ->
                    sb.append("\\u%04X".format(c))
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // ---------- 统计 ----------
    private fun stat(pkg: String): PkgStat = stats.getOrPut(pkg) { PkgStat() }

    fun noteEvent(pkg: String) {
        stat(pkg).events++
    }

    /** 一次 TEXT_CHANGED: readable=false 表示事件里读不到文本。 */
    fun noteTextChanged(pkg: String, readable: Boolean, normalized: String?) {
        val s = stat(pkg)
        s.textChanged++
        if (!readable) {
            s.textUnreadable++
        } else {
            s.lastText = normalized
        }
    }

    /** 读到过一次焦点输入框(可编辑)。 */
    fun noteInputRead(pkg: String?, className: String?, textReadable: Boolean, normalized: String?) {
        if (pkg == null) return
        val s = stat(pkg)
        s.inputSeen = true
        s.inputClass = className ?: s.inputClass
        if (textReadable) {
            s.inputTextReadable = true
            s.lastText = normalized
        }
    }

    /** 归一化后的文本命中了触发词。 */
    fun noteMatch(pkg: String?) {
        if (pkg == null) return
        stat(pkg).matches++
    }

    /** 观察到输入框变空(候选确认的那一次)。 */
    fun noteEmpty(pkg: String?) {
        if (pkg == null) return
        stat(pkg).empties++
    }

    /** 记录一次输入框节点的观察结果(同类名只记第一条, 避免刷屏)。 */
    fun noteInputNode(className: String, editable: Boolean, textReadable: Boolean, text: String?) {
        val key = "$className|$editable|$textReadable"
        if (!seenClasses.add(key)) return
        val desc = "类名=$className  可编辑=$editable  文本可读=$textReadable  当前=${escape(text)}"
        inputNodes.add(desc)
        while (inputNodes.size > 40) inputNodes.removeAt(0)
        log("输入框", desc)
    }

    fun clear() {
        lines.clear()
        inputNodes.clear()
        treeLines.clear()
        seenClasses.clear()
        stats.clear()
    }

    /**
     * 生成"每个目标应用分别断在哪一环"的结论。
     * 传进来的是 (包名, 显示名) 列表。
     *
     * 注意判定顺序: 命中/确认来自**事件自带文本**(微信的节点树不可见但事件带文本),
     * inputTextReadable 只反映"节点路径"是否读到过文本 —— 所以先看命中/确认,
     * 不能让"节点读不到"把真实进度盖掉(那正是 v1.0.6 微信被误诊为"链路走不通"的原因)。
     */
    fun verdict(targets: List<Pair<String, String>>): List<String> {
        if (targets.isEmpty()) return listOf("没有开启任何目标应用")
        return targets.map { (pkg, label) ->
            val s = stats[pkg]
            val body = when {
                s == null || s.events == 0 ->
                    "没收到任何事件。若你确实打开并操作过它，说明服务没连上（或被系统/ROM 限制了后台）"

                s.empties > 0 ->
                    "链路正常（命中 ${s.matches} 次，确认发送 ${s.empties} 次）" +
                            if (s.inputTextReadable) "" else "（节点树不可见，靠事件文本判定）"

                s.matches > 0 ->
                    "命中过 ${s.matches} 次触发词，但没观察到发送 —— 清空/发送确认信号没抓到"

                !s.inputTextReadable ->
                    "收到 ${s.events} 个事件，但输入框文本读不到（节点不可读，事件也没带文本）" +
                            (s.inputClass?.let { "。节点类名 $it" } ?: "") +
                            "。该客户端没把文本暴露给无障碍，这条链路走不通"

                else ->
                    "能读到输入框文本，但没命中过触发词。" +
                            "最后读到的内容：${escape(s.lastText)}。检查是否真的输入了 mj"
            }
            "$label：$body"
        }
    }
}
