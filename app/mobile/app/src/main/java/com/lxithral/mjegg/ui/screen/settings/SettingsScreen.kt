package com.lxithral.mjegg.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lxithral.mjegg.egg.MjAccessibilityService
import com.lxithral.mjegg.platform.SettingsStore
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** 设置主页: 外观入口 + 无障碍状态 + 诊断 + 关于。 */
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    isDark: Boolean,
    onOpenTheme: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    @Suppress("UNUSED_EXPRESSION")
    isDark
    val connected by produceState(initialValue = MjAccessibilityService.isConnected()) {
        while (true) {
            value = MjAccessibilityService.isConnected()
            delay(1000)
        }
    }
    val enabledInSettings = MjAccessibilityService.isEnabledInSettings(context)
    Scaffold(
        topBar = { TopAppBar(title = "设置", largeTitle = "设置") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            SmallTitle("外观")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = "主题设置",
                    summary = "深色模式 / 动态取色 / 主题色 / 底栏形态",
                    onClick = onOpenTheme,
                )
                BasicComponent(
                    title = "深色模式",
                    summary = settings.colorMode.label(),
                )
                SwitchPreference(
                    title = "动态取色 (Monet)",
                    summary = if (settings.monet) "跟随系统壁纸配色" else "使用预设主题色",
                    checked = settings.monet,
                    onCheckedChange = settings::updateMonet,
                )
                BasicComponent(
                    title = "当前形态",
                    summary = settings.bottomBarStyle.label(),
                )
            }

            SmallTitle("无障碍")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(
                    title = "服务状态",
                    summary = when {
                        connected -> "已连接（正在收事件）"
                        enabledInSettings -> "系统已勾选，但服务未连接（建议关闭后重新打开）"
                        else -> "未连接（彩蛋不会触发）"
                    },
                )
                ArrowPreference(
                    title = "打开系统无障碍设置",
                    summary = "在「已安装的服务」里找到 MJ 彩蛋",
                    onClick = { MjAccessibilityService.openAccessibilitySettings(context) },
                )
            }

            SmallTitle("排查")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = "诊断",
                    summary = "看目标应用发来的事件、读到的文本、输入框节点信息",
                    onClick = onOpenDiagnostics,
                )
            }

            SmallTitle("关于")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = "关于 MJ 彩蛋",
                    summary = "版本 / 开发者 / 开源信息",
                    onClick = onOpenAbout,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

internal fun com.lxithral.mjegg.platform.ColorMode.label(): String = when (this) {
    com.lxithral.mjegg.platform.ColorMode.SYSTEM -> "跟随系统"
    com.lxithral.mjegg.platform.ColorMode.LIGHT -> "浅色"
    com.lxithral.mjegg.platform.ColorMode.DARK -> "深色"
    com.lxithral.mjegg.platform.ColorMode.AMOLED -> "深色 (AMOLED 纯黑)"
}

internal fun com.lxithral.mjegg.platform.BottomBarStyle.label(): String = when (this) {
    com.lxithral.mjegg.platform.BottomBarStyle.STANDARD -> "标准 miuix 底栏"
    com.lxithral.mjegg.platform.BottomBarStyle.FLOATING -> "悬浮底栏"
    com.lxithral.mjegg.platform.BottomBarStyle.LIQUID_GLASS -> "液态玻璃底栏"
}
