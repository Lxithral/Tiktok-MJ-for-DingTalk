package com.lxithral.mjegg.ui.screen.module

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
import com.lxithral.mjegg.platform.SettingsStore
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 模块页: 素材与运行状态一览(纯展示, 用于核对两段动画的配置)。 */
@Composable
fun ModuleScreen(settings: SettingsStore) {
    Scaffold(
        topBar = { TopAppBar(title = "模块", largeTitle = "模块") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            SmallTitle("动画素材")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(
                    title = "坠落 (mj-drop-alpha.webp)",
                    summary = "带 alpha 通道 · 锚定右上角 · 含同步音效",
                )
                BasicComponent(
                    title = "荡绳 (mj-swing-alpha.webp)",
                    summary = "带 alpha 通道 · 锚定左上角 · 含同步音效",
                )
                BasicComponent(
                    title = "播放顺序",
                    summary = "两段自动交替 · 播完自动销毁 · 8 秒看门狗兜底",
                )
            }

            SmallTitle("运行状态")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(title = "累计触发", summary = "${settings.triggerCount} 次")
                BasicComponent(
                    title = "监控应用",
                    summary = settings.enabledPackages().joinToString("、").ifEmpty { "（未选择）" },
                )
                BasicComponent(
                    title = "动画高度",
                    summary = "屏幕高度的 ${settings.overlayHeight}%",
                )
                BasicComponent(
                    title = "冷却",
                    summary = "${settings.cooldownSeconds} 秒",
                )
            }

            SmallTitle("关于")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(title = "版本", summary = "1.0.0 (手机版)")
                BasicComponent(title = "作者", summary = "L'xithral")
                BasicComponent(
                    title = "素材来源",
                    summary = "qiu7c/Tiktok-MJ-for-Wechat（抖音 MJ 蜘蛛侠彩蛋）",
                )
            }

            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(
                    text = "桌面版（Windows）在同一仓库的 app/desktop 下，" +
                            "支持钉钉 / 微信 / QQ 三个客户端，以及 Windows 屏幕键盘输入。",
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
