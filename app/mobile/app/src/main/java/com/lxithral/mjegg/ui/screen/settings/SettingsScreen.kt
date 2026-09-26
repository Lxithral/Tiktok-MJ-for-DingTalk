package com.lxithral.mjegg.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lxithral.mjegg.platform.SettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 设置主页 —— 只放「入口」：外观/排查(开发者)/关于。
 *
 * 不重复展示主题设置里已有的开关(动态取色/深色模式/底栏形态)，
 * 不重复展示主页状态卡已有的无障碍服务状态。
 */
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    isDark: Boolean,
    onOpenTheme: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onRerunOobe: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                largeTitle = "设置",
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            SmallTitle("外观")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = "主题设置",
                    summary = "深色模式 / 动态取色 / 主题色 / 底栏 / 手势",
                    onClick = onOpenTheme,
                )
            }

            if (settings.devUnlocked) {
                SmallTitle("开发者模式")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "诊断",
                        summary = "看目标应用发来的事件、读到的文本、输入框节点信息",
                        onClick = onOpenDiagnostics,
                    )
                    ArrowPreference(
                        title = "重新运行首启引导",
                        summary = "调试 OOBE 流程（欢迎 / 无障碍 / 完成）",
                        onClick = onRerunOobe,
                    )
                }
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
