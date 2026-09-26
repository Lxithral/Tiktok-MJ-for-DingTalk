package com.lxithral.mjegg.ui.screen.feature

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
import com.lxithral.mjegg.egg.MjAccessibilityService
import com.lxithral.mjegg.platform.SettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 功能页: 监控哪些应用 + 播放参数 + 手动测试。 */
@Composable
fun FeatureScreen(settings: SettingsStore) {
    Scaffold(
        topBar = { TopAppBar(title = "功能", largeTitle = "功能") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            SmallTitle("监控的应用")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                SwitchPreferenceRow(
                    title = "微信",
                    summary = "com.tencent.mm",
                    checked = settings.targetWeChat,
                    onCheckedChange = settings::updateTargetWeChat,
                )
                SwitchPreferenceRow(
                    title = "QQ",
                    summary = "com.tencent.mobileqq",
                    checked = settings.targetQQ,
                    onCheckedChange = settings::updateTargetQQ,
                )
                SwitchPreferenceRow(
                    title = "钉钉",
                    summary = "com.alibaba.android.rimet",
                    checked = settings.targetDingTalk,
                    onCheckedChange = settings::updateTargetDingTalk,
                )
                SwitchPreferenceRow(
                    title = "抖音",
                    summary = "com.ss.android.ugc.aweme",
                    checked = settings.targetDouyin,
                    onCheckedChange = settings::updateTargetDouyin,
                )
            }

            SmallTitle("播放")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = "播放测试",
                    summary = if (MjAccessibilityService.isConnected()) "点一下立刻放一遍动画"
                    else "需要先开启无障碍服务",
                    onClick = { MjAccessibilityService.instance?.testPlay() },
                )
                SliderPreference(
                    value = settings.volume.toFloat(),
                    onValueChange = { settings.updateVolume(it.toInt()) },
                    title = "音效音量",
                    summary = "0 表示静音",
                    valueText = "${settings.volume}%",
                    valueRange = 0f..100f,
                    steps = 19,
                )
                SliderPreference(
                    value = settings.overlayHeight.toFloat(),
                    onValueChange = { settings.updateOverlayHeight(it.toInt()) },
                    title = "动画高度",
                    summary = "占屏幕高度的比例",
                    valueText = "${settings.overlayHeight}%",
                    valueRange = 30f..120f,
                    steps = 17,
                )
                SliderPreference(
                    value = settings.cooldownSeconds.toFloat(),
                    onValueChange = { settings.updateCooldownSeconds(it.toInt()) },
                    title = "冷却时间",
                    summary = "两次播放之间的最小间隔",
                    valueText = "${settings.cooldownSeconds} 秒",
                    valueRange = 0f..30f,
                    steps = 29,
                )
            }

            SmallTitle("说明")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = "只读取聊天输入框的文字变化来判断「你自己发送了 mj」，" +
                            "不会注入按键、不会模拟点击、不会替你发消息。" +
                            "两段动画（坠落 / 荡绳）会交替播放，分别贴右上角与左上角。",
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchPreferenceRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    top.yukonga.miuix.kmp.basic.BasicComponent(
        title = title,
        summary = summary,
        endActions = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}
