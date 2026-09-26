package com.lxithral.mjegg.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lxithral.mjegg.egg.MjAccessibilityService
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.component.StatusCard
import com.lxithral.mjegg.ui.component.StatusKind
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(settings: SettingsStore, isDark: Boolean, themeKey: String) {
    val context = LocalContext.current

    // 无障碍服务是系统持有的, 这里轮询它的连接状态(1 秒一次, 开销可忽略)
    val connected by produceState(initialValue = MjAccessibilityService.isConnected()) {
        while (true) {
            value = MjAccessibilityService.isConnected()
            delay(1000)
        }
    }
    val enabledInSettings = MjAccessibilityService.isEnabledInSettings(context)
    val targets = settings.enabledPackages().size

    val kind = when {
        !settings.eggEnabled -> StatusKind.LOADING
        connected -> StatusKind.WORKING
        else -> StatusKind.ERROR
    }
    val title = when {
        !settings.eggEnabled -> "彩蛋已停用"
        connected -> "彩蛋运行中"
        // 勾了但没连上: 多半是系统没绑定成功(或被 ROM 杀了), 这是最容易误判的一种状态
        enabledInSettings -> "服务已勾选, 但没连上"
        else -> "无障碍服务未开启"
    }
    val summary = when {
        !settings.eggEnabled -> "打开下面的开关即可恢复"
        connected -> "已监控 $targets 个应用 · 累计触发 ${settings.triggerCount} 次"
        enabledInSettings -> "系统没绑定上服务: 去无障碍设置里关掉、再重新打开一次"
        else -> "点下方按钮去系统设置里授权"
    }

    Scaffold(
        topBar = {
            TopAppBar(title = "MJ 彩蛋", largeTitle = "MJ 彩蛋")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            StatusCard(
                kind = kind,
                title = title,
                summary = summary,
                tag = if (connected) "ON" else "OFF",
                isDark = isDark,
                watermark = MiuixIcons.Send,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            SmallTitle("快捷开关")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(
                    title = "启用彩蛋",
                    summary = "关闭后不再响应任何触发词",
                    endActions = {
                        Switch(checked = settings.eggEnabled, onCheckedChange = settings::updateEggEnabled)
                    },
                )
            }

            if (!connected) {
                SmallTitle("授权")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = if (enabledInSettings) "重新打开无障碍服务" else "去开启无障碍服务",
                        summary = if (enabledInSettings)
                            "先在系统设置里关掉 MJ 彩蛋, 再重新打开"
                        else
                            "在「无障碍 → 已安装的服务」里打开 MJ 彩蛋",
                        onClick = { MjAccessibilityService.openAccessibilitySettings(context) },
                    )
                }
            }

            SmallTitle("触发规则")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                InfoRow("触发词", "mj / mjmj / MJ / MjMj …")
                InfoRow("不触发", "mjm / mjx / amj")
                InfoRow("只响应自己发送的", "对方发的 mj 不会触发")
                InfoRow("生效范围", "微信 / QQ / 钉钉 / 抖音 的聊天输入框")
            }

            SmallTitle("怎么用")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = "在微信、QQ、钉钉或抖音的聊天输入框里输入 mj 并发送，" +
                            "全屏会播放一段带透明通道的蜘蛛侠动画，播完自动消失，" +
                            "不挡操作也不会抢焦点。",
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
private fun InfoRow(label: String, value: String) {
    BasicComponent(
        title = label,
        summary = value,
    )
}
