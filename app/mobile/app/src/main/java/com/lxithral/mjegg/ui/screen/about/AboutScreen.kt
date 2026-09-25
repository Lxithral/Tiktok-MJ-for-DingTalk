package com.lxithral.mjegg.ui.screen.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 关于页：保持 miuix 分组卡片语言，作为二级 NavDisplay 路由示例。 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = "关于",
                largeTitle = "关于",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SmallTitle("应用")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(title = "MJ 彩蛋", summary = "Android 版 · 只读无障碍监听")
                BasicComponent(title = "版本", summary = "1.0.6")
                BasicComponent(title = "开发者", summary = "L'xithral")
            }
            SmallTitle("说明")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = "本应用只读取已授权目标应用的无障碍事件，不注入按键、不模拟点击、不自动发送消息。",
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
