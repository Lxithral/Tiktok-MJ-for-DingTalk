package com.lxithral.mjegg.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lxithral.mjegg.egg.EggDebug
import com.lxithral.mjegg.egg.MjAccessibilityService
import com.lxithral.mjegg.platform.SettingsStore
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 诊断页 —— 排查"某个客户端为什么没触发"。
 *
 * 顶部直接给**结论**: 每个目标应用分别断在哪一环(没连上 / 读不到文本 / 判据没走通)。
 * 下面才是原始材料: 观察到的输入框节点、事件流水。
 * 三种情况的处理方式完全不同, 所以不能只丢一堆日志让人猜。
 */
private val ALL_TARGETS = listOf(
    SettingsStore.PKG_WECHAT to "微信",
    SettingsStore.PKG_QQ to "QQ",
    SettingsStore.PKG_DINGTALK to "钉钉",
)

@Composable
fun DiagnosticsScreen(settings: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val connected by produceState(initialValue = MjAccessibilityService.isConnected()) {
        while (true) {
            value = MjAccessibilityService.isConnected()
            delay(1000)
        }
    }
    val enabledInSettings = MjAccessibilityService.isEnabledInSettings(context)
    // 结论每 2 秒重算一次(统计不是 Compose 状态, 用轮询取快照最简单可靠)
    val verdict by produceState(initialValue = emptyList<String>()) {
        while (true) {
            val enabled = settings.enabledPackages()
            value = EggDebug.verdict(ALL_TARGETS.filter { it.first in enabled })
            delay(2000)
        }
    }
    val lines = EggDebug.lines
    val nodes = EggDebug.inputNodes
    val treeLines = EggDebug.treeLines

    Scaffold(
        topBar = {
            TopAppBar(
                title = "诊断",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SmallTitle("结论（每个应用分别断在哪一环）")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (verdict.isEmpty()) {
                        Text(
                            text = "还没有数据。去微信/QQ/钉钉里发一条 mj，再回来这里看。",
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                        )
                    } else {
                        verdict.forEach { line ->
                            Text(
                                text = line,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            )
                        }
                    }
                }
            }

            SmallTitle("控件树导出")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = "导出当前窗口控件树",
                    summary = "在目标应用的聊天页点一下，回来点这里 —— 看清它到底把什么暴露给了无障碍",
                    onClick = {
                        MjAccessibilityService.instance?.dumpActiveWindowTree()
                            ?: run { EggDebug.log("控件树", "服务未连接") }
                    },
                )
                if (treeLines.isNotEmpty()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        treeLines.forEach { line ->
                            Text(
                                text = line,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            SmallTitle("状态")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(
                    title = "无障碍服务",
                    summary = when {
                        connected -> "已连接（正在收事件）"
                        enabledInSettings -> "系统设置里已勾选, 但服务没连上 —— " +
                                "去无障碍设置关掉再打开一次"
                        else -> "未开启（先回主页点「去开启无障碍服务」）"
                    },
                )
                BasicComponent(
                    title = "监控应用",
                    summary = settings.enabledPackages().joinToString("、").ifEmpty { "（未选择）" },
                )
                BasicComponent(
                    title = "累计日志",
                    summary = "${lines.size} 条",
                )
                ArrowPreference(
                    title = "打开系统无障碍设置",
                    onClick = { MjAccessibilityService.openAccessibilitySettings(context) },
                )
            }

            SmallTitle("观察到的输入框节点")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (nodes.isEmpty()) {
                    Text(
                        text = "还没有观察到输入框。去微信/QQ/钉钉的聊天里点一下输入框，" +
                                "回来这里就能看到客户端暴露的节点信息。",
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                    )
                } else {
                    Column(modifier = Modifier.padding(12.dp)) {
                        nodes.forEach { node ->
                            Text(
                                text = node,
                                modifier = Modifier.padding(vertical = 3.dp),
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            SmallTitle("事件日志")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (lines.isEmpty()) {
                        Text(
                            text = "暂无日志。",
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                        )
                    } else {
                        // 倒序显示, 最新的在最上面
                        lines.asReversed().forEach { line ->
                            Text(
                                text = line,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                ArrowPreference(
                    title = "清空诊断日志",
                    onClick = { EggDebug.clear() },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
