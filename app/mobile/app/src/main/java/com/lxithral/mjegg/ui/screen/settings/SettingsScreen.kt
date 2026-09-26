package com.lxithral.mjegg.ui.screen.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lxithral.mjegg.platform.SettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 设置主页 —— 只放「入口」：外观/开发者模式/关于。
 *
 * 不重复展示主题设置里已有的开关(动态取色/深色模式/底栏形态)，
 * 不重复展示主页状态卡已有的无障碍服务状态。
 */
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    padding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onOpenTheme: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onRerunOobe: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            SmallTitle("外观")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = "主题设置",
                    summary = "深色模式 / 动态取色 / 主题色 / 底栏 / 手势",
                    onClick = onOpenTheme,
                )
            }
        }

        if (settings.devUnlocked) {
            item {
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
        }

        item {
            SmallTitle("关于")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = "关于 MJ 彩蛋",
                    summary = "版本 / 开发者 / 开源信息",
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
