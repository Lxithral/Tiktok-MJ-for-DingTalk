package com.lxithral.mjegg.ui.screen.oobe

import android.graphics.RuntimeShader
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxithral.mjegg.egg.MjAccessibilityService
import com.lxithral.mjegg.platform.SettingsStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * OOBE 首启引导（按套壳指南 04 = HyperCeiler provision 复刻，无用户协议页）：
 *
 * 步骤 0 首屏：AGSL 彩色流动辉光背景（三团光晕随时间漂移）+ logo 两段弹性入场
 *   （scale 0.5→0.95→1.0, 440ms+700ms）+ 文字标上浮（100dp→0 弹性 ~1.7s）+
 *   白色大圆箭头按钮延迟 1340ms 浮现（0.9→1.0, 450ms）；
 *   **点按钮：以按钮 bounds 为圆心放大铺满全屏**（前景色从白过渡到 surface），落地进引导。
 * 步骤 1 无障碍（只读承诺 + 实时状态 + 跳系统设置）；
 * 步骤 2 基础设置（监控哪些应用）；
 * 步骤 3 完成（辉光背景 + 开始使用）。
 */
private const val DEBOUNCE_MS = 2000L

@Composable
fun OobeScreen(settings: SettingsStore, onDone: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var step by remember { mutableIntStateOf(0) }

    var expanding by remember { mutableStateOf(false) }
    val expandProgress = remember { Animatable(0f) }
    var buttonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()

        when (step) {
            0 -> SplashStep(
                density = density,
                onButtonBounds = { buttonBounds = it },
                onExpand = {
                    val now = System.currentTimeMillis()
                    if (!expanding && now - lastTapAt > DEBOUNCE_MS) {
                        lastTapAt = now
                        expanding = true
                    }
                },
            )

            1 -> PermissionStep(
                onOpenSettings = { MjAccessibilityService.openAccessibilitySettings(context) },
                onNext = { step = 2 },
            )

            2 -> BasicStep(settings, onNext = { step = 3 })

            else -> DoneGlowStep(onDone = onDone)
        }

        // —— 招牌转场: 以大按钮 bounds 为圆心放大铺满全屏, 前景色白→surface ——
        val bounds = buttonBounds
        if (expanding && bounds != null) {
            LaunchedEffect(Unit) {
                expandProgress.snapTo(0f)
                expandProgress.animateTo(
                    1f,
                    tween(450, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)),
                )
                step = 1
                expanding = false
                expandProgress.snapTo(0f)
            }
            val p = expandProgress.value
            val surface = MiuixTheme.colorScheme.surface
            val btnW = with(density) { bounds.width.toDp() }
            val btnH = with(density) { bounds.height.toDp() }
            val rootWdp = with(density) { rootW.toDp() }
            val rootHdp = with(density) { rootH.toDp() }
            val w = btnW + (rootWdp - btnW) * p
            val h = btnH + (rootHdp - btnH) * p
            val x = with(density) { (bounds.center.x - w.toPx() / 2f).roundToInt() }
            val y = with(density) { (bounds.center.y - h.toPx() / 2f).roundToInt() }
            val corner = btnH / 2f * (1f - p)
            Box(
                modifier = Modifier
                    .offset { IntOffset(x, y) }
                    .size(w, h)
                    .clip(RoundedCornerShape(corner))
                    .background(lerp(Color.White, surface, p)),
            )
        }
    }
}

/** 首屏: 辉光背景 + logo 两段弹性入场 + 文字标上浮 + 白色大圆箭头按钮。 */
@Composable
private fun SplashStep(
    density: androidx.compose.ui.unit.Density,
    onButtonBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    onExpand: () -> Unit,
) {
    GlowBackground(Modifier.fillMaxSize())

    var buttonIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1340)
        buttonIn = true
    }
    val btnScale = animateFloatAsState(
        targetValue = if (buttonIn) 1f else 0.9f,
        animationSpec = tween(450, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)),
        label = "btnScale",
    )
    val btnAlpha = animateFloatAsState(
        targetValue = if (buttonIn) 1f else 0f,
        animationSpec = tween(450),
        label = "btnAlpha",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.26f))

        // logo 两段弹性: scale 0.5→0.95(440ms sinOut)→1.0(700ms cubicOut), alpha 延迟 60ms
        val logoScale = remember { Animatable(0.5f) }
        val logoAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            coroutineScope {
                launch { logoAlpha.animateTo(1f, tween(300, delayMillis = 60)) }
            }
            logoScale.animateTo(0.95f, tween(440, easing = LinearOutSlowInEasing))
            logoScale.animateTo(1f, tween(700, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)))
        }
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    alpha = logoAlpha.value
                }
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFE0342F)),
            contentAlignment = Alignment.Center,
        ) {
            Text("MJ", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.weight(0.05f))

        // 文字标: translationY 100dp→0 弹性(~1.7s), alpha 延迟 300ms/1400ms
        val textY = remember { Animatable(100f) }
        val textAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            coroutineScope {
                launch { textAlpha.animateTo(1f, tween(1400, delayMillis = 300)) }
            }
            textY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 62f))
        }
        Text(
            text = "MJ 彩蛋",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                translationY = textY.value * density.density
                alpha = textAlpha.value
            },
        )

        Spacer(Modifier.weight(0.14f))

        // 白色大圆箭头按钮(全屏唯一可点物)
        Box(
            modifier = Modifier.weight(0.2f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer {
                        scaleX = btnScale.value
                        scaleY = btnScale.value
                        alpha = btnAlpha.value
                    }
                    .clip(CircleShape)
                    .background(Color.White)
                    .onGloballyPositioned { onButtonBounds(it.boundsInRoot()) }
                    .clickable(onClick = onExpand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Forward,
                    contentDescription = "开始",
                    tint = Color(0xFF5A6470),
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(Modifier.weight(0.2f))
    }
}

@Composable
private fun PermissionStep(onOpenSettings: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val a11y by produceState(initialValue = false to false) {
        while (true) {
            value = MjAccessibilityService.isConnected() to
                    MjAccessibilityService.isEnabledInSettings(context)
            delay(1000)
        }
    }
    val (connected, enabledInSettings) = a11y
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(72.dp))
        Text("开启无障碍服务", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "彩蛋靠无障碍服务只读监听聊天输入框的文本变化。\n全程只读，不会替你打字或发消息。",
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        Card(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
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
        if (!connected) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("去系统设置开启")
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("下一步", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun BasicStep(settings: SettingsStore, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(72.dp))
        Text("基础设置", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "选择要监控的聊天应用，随时可以在「功能」页改。",
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(28.dp))
        Card(Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "微信",
                summary = "com.tencent.mm",
                checked = settings.targetWeChat,
                onCheckedChange = settings::updateTargetWeChat,
            )
            SwitchPreference(
                title = "QQ",
                summary = "com.tencent.mobileqq",
                checked = settings.targetQQ,
                onCheckedChange = settings::updateTargetQQ,
            )
            SwitchPreference(
                title = "钉钉",
                summary = "com.alibaba.android.rimet",
                checked = settings.targetDingTalk,
                onCheckedChange = settings::updateTargetDingTalk,
            )
            SwitchPreference(
                title = "抖音",
                summary = "com.ss.android.ugc.aweme",
                checked = settings.targetDouyin,
                onCheckedChange = settings::updateTargetDouyin,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("下一步", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun DoneGlowStep(onDone: () -> Unit) {
    GlowBackground(Modifier.fillMaxSize())
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(120.dp))
        Text("一切就绪！", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "去微信 / QQ / 钉钉 / 抖音的聊天输入框里\n输入 mj 并发送，蜘蛛侠马上登场。",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("开始使用", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * AGSL 彩色流动辉光背景（参照 HyperCeiler GlowPainter）:
 * 三团光晕（MJ 红 / 蓝 / 紫）随时间缓慢漂移, 深色底。
 */
@Composable
private fun GlowBackground(modifier: Modifier) {
    val shader = remember {
        RuntimeShader(
            """
            uniform float2 resolution;
            uniform float time;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution.y;
                float t = time;
                float2 c1 = float2(0.5 + 0.28 * sin(t * 0.35), 0.32 + 0.10 * sin(t * 0.53 + 1.0));
                float2 c2 = float2(0.18 + 0.20 * sin(t * 0.29 + 2.0), 0.78 + 0.12 * cos(t * 0.43));
                float2 c3 = float2(0.86 + 0.16 * cos(t * 0.31 + 4.0), 0.72 + 0.10 * sin(t * 0.47 + 3.0));
                float g1 = exp(-pow(distance(uv, c1) * 2.9, 2.0));
                float g2 = exp(-pow(distance(uv, c2) * 3.4, 2.0));
                float g3 = exp(-pow(distance(uv, c3) * 3.2, 2.0));
                float3 col = float3(0.016, 0.016, 0.024);
                col += float3(0.60, 0.13, 0.11) * g1;
                col += float3(0.10, 0.24, 0.58) * g2;
                col += float3(0.32, 0.11, 0.48) * g3;
                return half4(col, 1.0);
            }
            """.trimIndent()
        )
    }
    val time = remember { Animatable(0f) }
    LaunchedEffect(shader) {
        val start = System.nanoTime()
        while (true) {
            val now = withFrameNanos { it }
            time.snapTo((now - start) / 1_000_000_000f)
        }
    }
    Box(
        modifier = modifier.drawBehind {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("time", time.value)
            drawRect(ShaderBrush(shader))
        }
    )
}
