package com.lxithral.mjegg.ui.screen.oobe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxithral.mjegg.egg.MjAccessibilityService
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * OOBE 首启引导（按套壳指南 04 的节奏, 去掉用户协议页）:
 *
 * ① 欢迎 —— logo 弹性入场(translationY+alpha, ~1.7s) + 按钮延迟放大(0.98→1, 延迟 600ms);
 * ② 无障碍 —— 说明为什么需要(只读承诺) + 实时状态 + 一键跳系统设置;
 * ③ 完成 —— 提示去聊天里发一条 mj 试试。
 *
 * 完成后 `oobe_done=true`, 之后启动不再进入。
 */
@Composable
fun OobeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    // 入场动画状态
    var entered by remember { mutableStateOf(false) }
    var buttonIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
        delay(600)
        buttonIn = true
    }
    val logoAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 60f),
        label = "logoAlpha",
    )
    val logoOffsetY = androidx.compose.animation.core.animateDpAsState(
        targetValue = if (entered) 0.dp else 100.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 60f),
        label = "logoOffsetY",
    )
    val buttonScale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (buttonIn) 1f else 0.98f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "buttonScale",
    )

    // 无障碍实时状态(1s 轮询): 引导页开着时用户去系统设置开启, 回来自动变绿
    val a11y by produceState(initialValue = false to false) {
        while (true) {
            value = MjAccessibilityService.isConnected() to
                    MjAccessibilityService.isEnabledInSettings(context)
            delay(1000)
        }
    }
    val (connected, enabledInSettings) = a11y

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))

            // 仿桌面图标: 红底白字 MJ(自适应图标 XML 不支持 painterResource, 自绘最稳)
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        translationY = logoOffsetY.value.toPx()
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFE0342F)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "MJ",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally { it / 4 } + fadeOut())
                    }
                },
                label = "step",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (current) {
                        0 -> WelcomeStep()
                        1 -> AccessibilityStep(
                            connected = connected,
                            enabledInSettings = enabledInSettings,
                            openA11ySettings = {
                                MjAccessibilityService.openAccessibilitySettings(context)
                            },
                        )
                        else -> DoneStep()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = buttonScale.value; scaleY = buttonScale.value },
            ) {
                Button(
                    onClick = {
                        when (step) {
                            0 -> step = 1
                            1 -> if (step == 1) step = 2
                            else -> onDone()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = when (step) {
                            0 -> "开始"
                            1 -> "下一步"
                            else -> "开始使用"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(8.dp))
    Text(
        text = "MJ 彩蛋",
        fontSize = MiuixTheme.textStyles.title1.fontSize,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "在聊天里自己发送 mj，全屏播放一段\n蜘蛛侠动画，播完自动消失。",
        textAlign = TextAlign.Center,
        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
    )
    Spacer(Modifier.height(28.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        OobePoint("只读检测", "不注入按键、不模拟点击、不自动发消息")
        OobePoint("四端支持", "微信 / QQ / 钉钉 / 抖音 的聊天输入框")
        OobePoint("即发即播", "无论回车、点发送还是用输入法发送键")
    }
}

@Composable
private fun AccessibilityStep(
    connected: Boolean,
    enabledInSettings: Boolean,
    openA11ySettings: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = "开启无障碍服务",
        fontSize = MiuixTheme.textStyles.title1.fontSize,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "彩蛋靠无障碍服务只读监听聊天输入框的文本变化。\n全程只读，不会替你打字或发消息。",
        textAlign = TextAlign.Center,
        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
    )
    Spacer(Modifier.height(28.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = when {
                    connected -> "✓ 已连接（正在收事件）"
                    enabledInSettings -> "系统已勾选，但服务未连接 —— 请关闭后重新打开一次"
                    else -> "尚未开启"
                },
                color = if (connected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    if (!connected) {
        Button(
            onClick = openA11ySettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("去系统设置开启")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "开启后回到本页，状态会自动变绿；也可以先跳过，稍后在主页开启。",
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        )
    }
}

@Composable
private fun DoneStep() {
    Spacer(Modifier.height(8.dp))
    Text(
        text = "一切就绪！",
        fontSize = MiuixTheme.textStyles.title1.fontSize,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "去微信 / QQ / 钉钉 / 抖音的聊天输入框里\n输入 mj 并发送，蜘蛛侠马上登场。",
        textAlign = TextAlign.Center,
        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
    )
}

@Composable
private fun OobePoint(title: String, summary: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
        )
    }
}
