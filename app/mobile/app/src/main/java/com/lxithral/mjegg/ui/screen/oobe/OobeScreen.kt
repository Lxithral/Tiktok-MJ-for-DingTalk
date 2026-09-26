package com.lxithral.mjegg.ui.screen.oobe

import android.graphics.RuntimeShader
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * OOBE 首启引导 —— 严格复刻《04-HyperCeiler-OOBE引导与动效实现》：
 *
 * 首屏: AGSL 双层噪声辉光（附录 A glow.glsl 逐字）+ 0.2× 分辨率渲染放大 + 16ms 帧循环 +
 *   uTime ping-pong 2↔120；布局 上30%/90dp logo/中下40%(字标+按钮)/下20%；
 *   主标题 32sp、副标题 14sp(tertiary)、标题容器 35dp 水平边距；
 *   大圆按钮 **没有纯白按钮**：blur=玻璃灰(#CC4A4A4A 系混合色)，lite=60% 黑圆 + 白箭头 29×20dp；
 *   入场：logo scale 0.5→0.95(440ms sinOut)→1.0(700ms cubicOut) + alpha 延迟 60ms；
 *   文字 translationY 100dp→0(1700ms) + alpha(1400ms, 延迟 300ms)；
 *   按钮延迟 1340ms(scale 0.9→1 cubicOut 450ms + alpha 500ms)；
 *   2500ms displayOsAndo 节拍（logo 显示态收敛，无额外动作）。
 *
 * 招牌转场: 点按钮 2 秒防抖 → 按钮隐藏 → 以按钮 bounds 圆心放大铺满全屏(scale+圆角插值),
 *   前景色垫底(非模糊 #99000000 / 模糊开 #00ffffff) → 落地进第一页。
 *
 * 页间翻页: 进入 translate 350ms(accelerate_decelerate, 100%→0) + 离开 alpha 360ms(sine_in_out 1→0)。
 *
 * 流程: 首屏 → 无障碍 → 基础设置 → 完成（无用户协议页）。
 * 降级: blur 关闭或无 RuntimeShader → 静态背景 + lite 按钮 + 无入场动画。
 */
private const val DEBOUNCE_MS = 2000L

private val CUBIC_OUT = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)   // Folme cubicOut
private val SIN_OUT = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1f)     // Folme sinOut(440ms)
private val ACCEL_DECEL = FastOutSlowInEasing                          // accelerate_decelerate
private val SINE_IN_OUT = LinearOutSlowInEasing                        // sine_in_out(360ms 淡出)

// —— 玻璃混合色（文档 §1.3 int 数组换算, 照抄不翻译）——
private val GLASS_GREY = Color(0xCC4A4A4A)      // -867546550 → 80% 灰
private val GLASS_DARK = Color(0xFF4F4F4F)      // -11579569 → 深灰
private val GLASS_GREEN = Color(0xFF1AF200)     // -15011328 → 绿调
private val BTN_GLASS_BASE = Color(0xFF2E2E2E)  // -13750738 → 深灰
private const val FOREGROUND_FILL = 0x99000000  // anim_foreground_color (非模糊)

@Composable
fun OobeScreen(settings: SettingsStore, onDone: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val shaderSupported = isRuntimeShaderSupported()
    val glowActive = shaderSupported
    val blurGlass = settings.enableBlur && shaderSupported

    var step by remember { mutableIntStateOf(0) }
    var expanding by remember { mutableStateOf(false) }
    val expandProgress = remember { Animatable(0f) }
    var buttonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    // §10 isFirstBoot 语义: 只有真正首次进入首屏才放 admission 圆环; 从下一页返回不放圈
    var firstEntry by remember { mutableStateOf(true) }

    // §3.5 transitToPrevious: OOBE 内系统返回 = 回上一步(镜像翻页), 首屏再返回才退出
    androidx.activity.compose.BackHandler(enabled = step > 0) {
        if (step == 1) firstEntry = false   // 回首屏不放圈(needAdmission(false))
        step -= 1
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
    ) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()

        // —— §4.2 页间翻页: 进入 translate 350ms accelerate_decelerate; 离开 alpha 360ms sine_in_out ——
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(tween(350, easing = ACCEL_DECEL)) { it } togetherWith
                        fadeOut(tween(360, easing = SINE_IN_OUT))
                } else {
                    slideInHorizontally(tween(350, easing = ACCEL_DECEL)) { -it } togetherWith
                        fadeOut(tween(360, easing = SINE_IN_OUT))
                }
            },
            label = "oobe",
        ) { target ->
            when (target) {
            0 -> SplashStep(
                glowActive = glowActive,
                blurGlass = blurGlass,
                density = density,
                admission = firstEntry,
                onButtonBounds = { buttonBounds = it },
                buttonHidden = expanding,
                onExpand = {
                    val now = System.currentTimeMillis()
                    if (!expanding && now - lastTapAt > DEBOUNCE_MS) {
                        lastTapAt = now
                        firstEntry = false   // §10: 点按钮进第一页即视为非首次
                        expanding = true
                    }
                },
            )

            1 -> {
                // §13 单门控范式: 服务可用才允许下一步, 否则灰 0.5; 1s 轮询实时刷新
                val a11y by produceState(initialValue = false to false) {
                    while (true) {
                        value = MjAccessibilityService.isConnected() to
                                MjAccessibilityService.isEnabledInSettings(context)
                        delay(1000)
                    }
                }
                val (connected, enabledInSettings) = a11y
                GuideStep(
                    title = "开启无障碍服务",
                    subtitle = "彩蛋靠无障碍服务只读监听聊天输入框的文本变化。\n全程只读，不会替你打字或发消息。",
                    onNext = { step = 2 },
                    nextEnabled = connected || enabledInSettings,
                ) {
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
                        Button(
                            onClick = { MjAccessibilityService.openAccessibilitySettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("去系统设置开启") }
                    }
                }
            }

            2 -> GuideStep(
                title = "基础设置",
                subtitle = "选择要监控的聊天应用，随时可以在「功能」页改。",
                onNext = { step = 3 },
            ) {
                Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "微信", summary = "com.tencent.mm",
                        checked = settings.targetWeChat,
                        onCheckedChange = settings::updateTargetWeChat,
                    )
                    SwitchPreference(
                        title = "QQ", summary = "com.tencent.mobileqq",
                        checked = settings.targetQQ,
                        onCheckedChange = settings::updateTargetQQ,
                    )
                    SwitchPreference(
                        title = "钉钉", summary = "com.alibaba.android.rimet",
                        checked = settings.targetDingTalk,
                        onCheckedChange = settings::updateTargetDingTalk,
                    )
                    SwitchPreference(
                        title = "抖音", summary = "com.ss.android.ugc.aweme",
                        checked = settings.targetDouyin,
                        onCheckedChange = settings::updateTargetDouyin,
                    )
                }
            }

            else -> DoneStep(glowActive = glowActive, blurGlass = blurGlass, onDone = onDone)
            }
        }

        // —— 招牌转场: 位图放大铺满(§4.1 Compose 复刻), 前景色垫底(§1.2) ——
        val bounds = buttonBounds
        if (expanding && bounds != null) {
            LaunchedEffect(Unit) {
                expandProgress.snapTo(0f)
                expandProgress.animateTo(1f, tween(450, easing = CUBIC_OUT))
                step = 1
                expanding = false
                expandProgress.snapTo(0f)
            }
            val p = expandProgress.value
            val surface = MiuixTheme.colorScheme.surface
            val btnSize = with(density) { bounds.width.toDp() }
            val rootWdp = with(density) { rootW.toDp() }
            val rootHdp = with(density) { rootH.toDp() }
            val w = btnSize + (rootWdp - btnSize) * p
            val h = btnSize + (rootHdp - btnSize) * p
            val x = with(density) { (bounds.center.x - w.toPx() / 2f).roundToInt() }
            val y = with(density) { (bounds.center.y - h.toPx() / 2f).roundToInt() }
            val corner = btnSize / 2f * (1f - p)
            val btnFill = if (blurGlass) GLASS_GREY else Color(FOREGROUND_FILL)

            // 前景色垫底: 模糊开=透明(#00ffffff), 非模糊=#99000000
            if (!blurGlass) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(FOREGROUND_FILL).copy(alpha = p))
                )
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(x, y) }
                    .size(w, h)
                    .clip(RoundedCornerShape(corner))
                    .background(lerp(btnFill, surface, p * p)),
            )
        }
    }
}

/** 首屏（§1 布局比例 / §5 入场时序 / §6 按钮样式）。admission=是否播放开场圆环+遮罩（§5.6）。 */
@Composable
private fun SplashStep(
    glowActive: Boolean,
    blurGlass: Boolean,
    density: Density,
    admission: Boolean,
    onButtonBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    buttonHidden: Boolean,
    onExpand: () -> Unit,
) {
    // 辉光背景（附录 A shader + GlowPainter uniform 全表）；lite=静态深色渐变
    GlowCanvas(
        modifier = Modifier.fillMaxSize(),
        active = glowActive,
        admission = admission,
        circleYOffsetFrac = 0.30f + 45f / 1000f, // logo 中心 ≈ 上30% + 45dp
    )

    // 入场动画（lite 降级: 全部瞬时落位, 不播动画）
    val logoScale = remember { Animatable(if (glowActive) 0.5f else 1f) }
    val logoAlpha = remember { Animatable(if (glowActive) 0f else 1f) }
    val textY = remember { Animatable(if (glowActive) 100f else 0f) }
    val textAlpha = remember { Animatable(if (glowActive) 0f else 1f) }
    var buttonIn by remember { mutableStateOf(!glowActive) }

    LaunchedEffect(Unit) {
        if (!glowActive) return@LaunchedEffect
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(300, delayMillis = 60)) }
            launch { textAlpha.animateTo(1f, tween(1400, delayMillis = 300)) }
            launch { textY.animateTo(0f, tween(1700, easing = FastOutSlowInEasing)) }
            launch {
                delay(1340)   // 按钮最后浮现
                buttonIn = true
            }
        }
        logoScale.animateTo(0.95f, tween(440, easing = SIN_OUT))
        logoScale.animateTo(1f, tween(700, easing = CUBIC_OUT))
    }
    // §10 displayOsAndoDelay 兜底: 2500ms 强制恢复 logo/按钮可见可点,
    // 防动画回调未触发导致按钮永久不可点
    LaunchedEffect(Unit) {
        delay(2500)
        buttonIn = true
        logoAlpha.snapTo(1f)
        logoScale.snapTo(1f)
    }

    val btnScale by animateFloatAsState(
        targetValue = if (buttonIn) 1f else 0.9f,
        animationSpec = tween(450, easing = CUBIC_OUT),
        label = "btnScale",
    )
    val btnAlpha by animateFloatAsState(
        targetValue = if (buttonIn) 1f else 0f,
        animationSpec = tween(500),
        label = "btnAlpha",
    )

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(0.30f))                      // 上 30% 留白

        // logo 90dp, 玻璃混合色(lite: 深色实心)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(90.dp)
                .graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    alpha = logoAlpha.value
                }
                .clip(RoundedCornerShape(22.dp))
                .background(if (blurGlass) GLASS_GREY else Color(0xFF1A1A22)),
            contentAlignment = Alignment.Center,
        ) {
            if (blurGlass) {
                Canvas(Modifier.size(90.dp)) {
                    drawRoundRect(
                        color = GLASS_DARK,
                        style = Stroke(width = 2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                    )
                    drawCircle(color = GLASS_GREEN.copy(alpha = 0.25f), radius = size.minDimension / 2f - 6f, style = Stroke(2f))
                }
            }
            Text("MJ", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .weight(0.40f)                              // 中下 40%: 字标 + 按钮
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))                  // 字标 marginTop 20dp

            // 字标 + 副标题（§7: 32sp / 14sp tertiary / 容器 35dp 水平 + 30dp 底）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = textY.value * density.density
                        alpha = textAlpha.value
                    }
                    .padding(horizontal = 35.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "MJ 彩蛋",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.height(50.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "把蜘蛛侠带进你的聊天 —— 发一条 mj 即刻上演",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.weight(1f))

            // 大圆按钮（70dp）: blur=玻璃灰圆, lite=60% 黑圆; 白箭头 29×20dp; 转场期间隐藏
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .graphicsLayer {
                            scaleX = btnScale
                            scaleY = btnScale
                            alpha = if (buttonHidden) 0f else btnAlpha
                        }
                        .clip(CircleShape)
                        .background(if (blurGlass) GLASS_GREY else Color(0x99000000))
                        .onGloballyPositioned { onButtonBounds(it.boundsInRoot()) }
                        .clickable(onClick = onExpand),
                    contentAlignment = Alignment.Center,
                ) {
                    ArrowIcon()
                }
            }
            Spacer(Modifier.height(4.5.dp))
        }

        Spacer(Modifier.weight(0.20f))                      // 下 20% 留白
    }
}

/** 白色粗箭头 29×20dp（provision_icon_arrow）。 */
@Composable
private fun ArrowIcon() {
    Canvas(Modifier.size(29.dp, 20.dp)) {
        val w = size.width
        val h = size.height
        val shaft = h * 0.26f
        val headW = w * 0.42f
        val path = Path().apply {
            moveTo(0f, h / 2f - shaft / 2f)
            lineTo(w - headW, h / 2f - shaft / 2f)
            lineTo(w - headW, 0f)
            lineTo(w, h / 2f)
            lineTo(w - headW, h)
            lineTo(w - headW, h / 2f + shaft / 2f)
            lineTo(0f, h / 2f + shaft / 2f)
            close()
        }
        drawPath(path, Color.White)
    }
}

/** 向导页（§7 排版 + §5 centerPageAnim/endPageAnim 入场 + §4.2 翻页由 AnimatedContent 承担）。
 *  §13 门控范式: nextEnabled=false 时「下一步」置灰 0.5 alpha 不可点, 状态恢复自动亮起。 */
@Composable
private fun GuideStep(
    title: String,
    subtitle: String,
    onNext: () -> Unit,
    nextEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    // centerPageAnim: 中部内容 alpha 500ms + translationY 30dp→0
    val centerAlpha = remember { Animatable(0f) }
    val centerY = remember { Animatable(30f) }
    // endPageAnim: 底部内容 translationY 20dp→0(1250ms) + alpha(1050ms)
    val endAlpha = remember { Animatable(0f) }
    val endY = remember { Animatable(20f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { centerAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            launch { centerY.animateTo(0f, tween(700, easing = CUBIC_OUT)) }
            launch { endAlpha.animateTo(1f, tween(1050, easing = FastOutSlowInEasing)) }
            launch { endY.animateTo(0f, tween(1250, easing = FastOutSlowInEasing)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 35.dp)
                .graphicsLayer {
                    alpha = centerAlpha.value
                    translationY = centerY.value * 3f
                },
        ) {
            Spacer(Modifier.height(72.dp))
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.height(50.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = centerAlpha.value
                    translationY = centerY.value * 3f
                },
            ) { content() }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 35.dp)
                .graphicsLayer {
                    alpha = endAlpha.value * if (nextEnabled) 1f else 0.5f
                    translationY = endY.value * 3f
                },
        ) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                enabled = nextEnabled,
            ) {
                Text("下一步", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** 完成页: 辉光 + logo + 32sp 标题 + 14sp 副标题。 */
@Composable
private fun DoneStep(glowActive: Boolean, blurGlass: Boolean, onDone: () -> Unit) {
    // §8.1: 完成页辉光用 start(false) —— 无圈仅流动背景
    GlowCanvas(
        modifier = Modifier.fillMaxSize(),
        active = glowActive,
        admission = false,
        circleYOffsetFrac = 0.35f,
    )

    // §8.1 入场: logo translationY 100→0(quartOut 1500ms)+alpha; 按钮 alpha sinOut 450ms 延迟 1000ms
    val logoY = remember { Animatable(if (glowActive) 100f else 0f) }
    val logoAlpha = remember { Animatable(if (glowActive) 0f else 1f) }
    val btnAlpha = remember { Animatable(if (glowActive) 0f else 1f) }
    // §8.2 (a) 点「开始使用」引导内容缩 1→0.8 弹簧 + 淡出 sinOut 360ms, 播完才接力主页
    val pageScale = remember { Animatable(1f) }
    val pageAlpha = remember { Animatable(1f) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!glowActive) return@LaunchedEffect
        coroutineScope {
            // quartOut(1500ms) ≈ CubicBezier(0.25,1,0.5,1)
            launch {
                logoY.animateTo(0f, tween(1500, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)))
                logoAlpha.animateTo(1f, tween(600))
            }
            launch {
                delay(1000)          // 按钮延迟浮现
                btnAlpha.animateTo(1f, tween(450, easing = SIN_OUT))
            }
        }
    }

    if (leaving) {
        LaunchedEffect(Unit) {
            coroutineScope {
                launch {
                    // spring(1.0, 0.36) 弹簧缩小
                    pageScale.animateTo(0.8f, spring(dampingRatio = 0.36f, stiffness = 400f))
                }
                launch {
                    pageAlpha.animateTo(0f, tween(360, easing = SIN_OUT))
                }
            }
            onDone()   // (b) 接力: 主页 1.3→1.0 弹簧放大淡入(MainScreen 侧播)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 35.dp)
            .graphicsLayer {
                scaleX = pageScale.value
                scaleY = pageScale.value
                alpha = pageAlpha.value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(120.dp))
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    translationY = logoY.value * 3f
                    alpha = logoAlpha.value
                }
                .clip(RoundedCornerShape(22.dp))
                .background(if (blurGlass) GLASS_GREY else Color(0xFF1A1A22)),
            contentAlignment = Alignment.Center,
        ) {
            Text("MJ", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "一切就绪！",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(50.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = "去微信 / QQ / 钉钉 / 抖音的聊天输入框里\n输入 mj 并发送，蜘蛛侠马上登场。",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (!leaving) leaving = true },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = btnAlpha.value },
        ) {
            Text("开始使用", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * 辉光画布（附录 A GlowPainter uniform 全表 + GlowController tickPingPong + RenderViewLayout）:
 * 0.2× 分辨率渲染放大（画布居中+黑底）、16ms 帧循环、uTime ping-pong 2↔120（从 0 起步）。
 * admission=false 时 uShowCircle=0（不放开场圆环，仅流动背景——对应 needAdmission(false)）。
 * active=false(lite) 时退化为静态深色渐变。
 */
@Composable
private fun GlowCanvas(
    modifier: Modifier,
    active: Boolean,
    admission: Boolean,
    circleYOffsetFrac: Float,
) {
    if (!active) {
        Box(
            modifier.background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF0A0A12), Color(0xFF12121E))
                )
            )
        )
        return
    }
    val shader = remember {
        runCatching {
            RuntimeShader(GLOW_GLSL).apply {
            // GlowPainter 构造器的 uniform 全表 —— 数值照抄, 不许改
            setFloatUniform("uScale2", 0.82f)
            setFloatUniform("uSpeed2", 0.49f)
            setFloatUniform("uColorInMin", 0.3f)
            setFloatUniform("uColorInMax", 1.0f)
            setFloatUniform("uColorOutMin", 0.3f)
            setFloatUniform("uColorOutMax", 0.86f)
            setFloatUniform("uColorMidPoint", 0.47f)
            setFloatUniform("uUseOklab", 1.0f)
            setFloatUniform("uColorBlack", floatArrayOf(0.961f, 0.157f, 0.157f))  // 红橙(名不副实!)
            setFloatUniform("uColorMid", floatArrayOf(0.604f, 0.659f, 0.961f))    // 淡蓝紫
            setFloatUniform("uColorWhite", floatArrayOf(0.302f, 0.29f, 0.843f))   // 深蓝紫(名不副实!)
            setFloatUniform("uScale", 1.3f)
            setFloatUniform("uSpeed", 0.4f)
            setFloatUniform("uBrightnessInMin", 0.25f)
            setFloatUniform("uBrightnessInMax", 1.0f)
            setFloatUniform("uBrightnessOutMin", 0.25f)
            setFloatUniform("uBrightnessOutMax", 1.0f)
            setFloatUniform("uShowCircle", if (admission) 1.0f else 0.0f)
            setFloatUniform("uCircleThickness", 0.4f)
            setFloatUniform("uCircleFinalRadius", 1.0f)
            setFloatUniform("uCircleSpeed", 0.9f)
            setFloatUniform("uCircleColorFreq", 1.0f)
            setFloatUniform("uCircleColorSpeed", 0.0f)
            setFloatUniform("uCircleEasing", 1.4f)
            setFloatUniform("uCircleAnimationOffset", 0.0f)
            setFloatUniform("uMaskDelay", 0.3f)
            setFloatUniform("uMaskThickness", 0.3f)
            setFloatUniform("uCircleScreenBlend", 1.0f)
            setFloatUniform("uCircleAddBlend", 0.04f)
            setFloatUniform("uCircleColorOffset", 0.25f)
            setFloatUniform("uCircleUVDistort", 0.0f)
            setFloatUniform("uColorToDistortWidthRatio", 0.6f)
            setFloatUniform("uDistortStartTime", 0.2f)
            setFloatUniform("uDistortEndTime", 0.3f)
            setFloatUniform("uDistortStart", 0.0f)
            setFloatUniform("uDistortEnd", 1.0f)
            setFloatUniform("uStripeFrequency", 0.0f)
            setFloatUniform("uStripeStrengthX", 0.0f)
            setFloatUniform("uStripeStrengthY", 0.0f)
            setFloatUniform("uStripeUVDistort", 0.0f)
        }
    }.getOrNull()   // shader 异常(如个别机型 AGSL 兼容性)时回退静态背景, 不崩溃
    }

    // GlowController: uTime 从 0 起步, ping-pong 2↔120 真实时间推进
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var dir = 1f
        var last = System.nanoTime()
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            var v = time.value + dir * dt
            if (v >= 120f) { v = 120f; dir = -1f }
            if (v <= 2f) { v = 2f; dir = 1f }
            time.snapTo(v)
        }
    }

    Box(modifier) {
        // RenderViewLayout 同款策略: 0.2× 分辨率渲染 + 放大; 画布**居中**且黑底(attachView 的 -16777216)
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.2f)
                    .fillMaxHeight(0.2f)
                    .background(Color.Black)
                    .graphicsLayer {
                        scaleX = 5f
                        scaleY = 5f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .drawBehind {
                        val s = shader
                        if (s == null) {
                            drawRect(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color(0xFF0A0A12), Color(0xFF12121E))
                                )
                            )
                            return@drawBehind
                        }
                        s.setFloatUniform("uResolution", size.width, size.height)
                        s.setFloatUniform("uTime", time.value)
                        s.setFloatUniform(
                            "uCircleYOffset",
                            (size.height / 2f - circleYOffsetFrac * size.height) / size.height,
                        )
                        drawRect(ShaderBrush(s))
                    }
            )
        }
    }
}
