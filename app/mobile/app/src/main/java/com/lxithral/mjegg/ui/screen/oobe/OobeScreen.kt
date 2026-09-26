package com.lxithral.mjegg.ui.screen.oobe

import android.graphics.RuntimeShader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/*
 * OOBE 首启引导 —— 逐项对照 HyperCeiler `library/provision` 实现（AGPL-3.0）：
 * https://github.com/ReChronoRain/HyperCeiler
 *
 * 参照源文件（数值/时序/布局均照抄源码，未自创）：
 *   fragment/StartupFragment.java        首屏装配 + 2s 防抖 + displayOsAndoDelay(2500ms) 兜底
 *   utils/AnimHelper.java                startPageLogoAnim / startPageBtnAnim(Folme) 时序
 *   renderengine/GlowController.java     uTime 从 0 起、ping-pong 2↔120、16ms 帧循环
 *   renderengine/GlowPainter.java        uniform 全表
 *   renderengine/RenderViewLayout.java   0.2× 居中渲染 + scale 5× + 黑底(-16777216)
 *   fragment/PermissionSettingsFragment  下一步单门控: 禁用 + HALF_ALPHA(0.5)
 *   widget/PermissionItemView.java       权限行: 56dp + 右侧蓝 check(仅显隐)
 *   fragment/CongratulationFragment.java 完成页入场/退场 + startHome 转场
 *   fan/provision/ProvisionBaseActivity  delayEnableButton(1000ms) + 返回钮 40dp
 *   res/layout/provision_startup_layout  权重 30/90dp/40/20 + 按钮 70dp(padding 4.5dp)
 *   res/layout/provision_congratulation  权重 18/40/0.2 + 按钮 50dp 圆角 16dp #99000000
 *   res/layout/provision_permission_*.xml 权限行 56dp / 次级文本 13sp
 *   res/anim/provision_slide_*.xml       页间纯平移 350ms accelerate_decelerate（无淡出）
 *   res/anim/enter_home_anim.xml         主页进场 1.3→1.0(619ms 弹簧 d0.65/r0.55) + α230ms offset60
 *   res/anim/provision_out_anim.xml      引导退场 alpha 360ms sine_in_out
 *
 * MJ 适配（非 HyperCeiler 内容）：logo=红底 MJ 方块、字标「MJ 彩蛋」、各页文案、
 * 权限行=无障碍服务、基础设置=监听应用开关。用户协议页按既定决定不做。
 */

// —— Folme 缓动的贝塞尔等价（EaseManager/FolmeEase 常用档）——
private val CUBIC_OUT = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)     // cubicOut
private val SIN_OUT = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1f)       // sinOut
private val QUART_OUT = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)           // quartOut
private val ACCEL_DECEL = FastOutSlowInEasing                            // accelerate_decelerate
private val SINE_IN_OUT = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)        // sine_in_out(pathInterpolator)

private const val DEBOUNCE_MS = 2000L          // StartupFragment 点击防抖
private const val DISPLAY_OS_ANDO_MS = 2500L   // displayOsAndoDelay 兜底
private const val BUTTON_IN_DELAY_MS = 1340L   // startPageBtnAnim setDelay(1340)
private const val BUTTON_IN_DUR_MS = 450       // FolmeEase.cubicOut(450)
private const val BUTTON_ENABLE_DELAY_MS = 1000L // delayEnableButton

// —— 颜色照抄 res/values/colors.xml 与各 drawable, 勿按名翻译 ——
private val GLASS_GREY = Color(0xCC4A4A4A)      // 模糊按钮玻璃底(blender -867546550)
private val BTN_LITE = Color(0x99000000)        // provision_next_lite: 60% 黑圆
private const val FOREGROUND_FILL = 0x99000000  // anim_foreground_color (非模糊)
private val PROVISION_BLUE = Color(0xFF3482FF)  // provision_confirm_background
private val CHECK_BLUE = Color(0xFF277AF7)      // provision_picker_btn_radio
private val STATE_TEXT_COLOR = Color(0xBF000000) // system_state_text #BF000000
private val MJ_LOGO_RED = Color(0xFFE0342F)     // MJ 红底 logo（自绘, 见文件头）

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
    var buttonBounds by remember { mutableStateOf<Rect?>(null) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    // needAdmission 语义: 只有真正首入首屏放圆环; 点按钮前进或返回回首屏均不再放圈
    var firstEntry by remember { mutableStateOf(true) }

    // transitToPrevious: OOBE 内返回=回上一步(镜像滑动); 首屏返回交系统(退出)
    BackHandler(enabled = step > 0) {
        if (step == 1) firstEntry = false
        step -= 1
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
    ) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()

        // —— 页间翻页 = provision_slide_*.xml: 纯平移 350ms accelerate_decelerate, 无淡出 ——
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                when {
                    // 0→1 由按钮放大转场落地, 翻页动画不掺和(平地替换, 被转场层遮住)
                    initialState == 0 && targetState == 1 ->
                        EnterTransition.None togetherWith ExitTransition.None
                    targetState > initialState ->
                        slideInHorizontally(tween(350, easing = ACCEL_DECEL)) { it } togetherWith
                            slideOutHorizontally(tween(350, easing = ACCEL_DECEL)) { -it }
                    else ->
                        slideInHorizontally(tween(350, easing = ACCEL_DECEL)) { -it } togetherWith
                            slideOutHorizontally(tween(350, easing = ACCEL_DECEL)) { it }
                }
            },
            label = "oobe",
        ) { target ->
            when (target) {
                0 -> SplashStep(
                    glowActive = glowActive,
                    blurGlass = blurGlass,
                    rootHeightPx = rootH,
                    admission = firstEntry,
                    entryAnim = firstEntry,
                    onButtonBounds = { buttonBounds = it },
                    buttonHidden = expanding,
                    onExpand = {
                        val now = System.currentTimeMillis()
                        if (!expanding && now - lastTapAt > DEBOUNCE_MS) {
                            lastTapAt = now
                            firstEntry = false
                            expanding = true
                        }
                    },
                )

                1 -> PermissionStep(
                    onBack = { if (step > 0) { firstEntry = false; step -= 1 } },
                    onNext = { step = 2 },
                )

                2 -> BasicStep(
                    settings = settings,
                    onBack = { if (step > 0) step -= 1 },
                    onNext = { step = 3 },
                )

                else -> DoneStep(glowActive = glowActive, blurGlass = blurGlass, onDone = onDone)
            }
        }

        // —— 按钮放大转场: ActivityOptions.makeScaleUpAnim 的 Compose 等价 ——
        // 圆钮 bounds 起手放大成圆角矩形铺满, 前景色垫底(模糊开 #00ffffff / 非模糊 #99000000),
        // 铺满后落地第一页, 转场层整体淡出(几何保持铺满, 只淡 alpha)。
        val bounds = buttonBounds
        if (expanding && bounds != null) {
            val overlayAlpha = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                expandProgress.snapTo(0f)
                overlayAlpha.snapTo(1f)
                expandProgress.animateTo(1f, tween(BUTTON_IN_DUR_MS, easing = CUBIC_OUT))
                step = 1
                overlayAlpha.animateTo(0f, tween(200))
                expanding = false
            }
            val p = expandProgress.value
            val layerAlpha = overlayAlpha.value
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

            if (!blurGlass) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = layerAlpha }
                        .background(Color(FOREGROUND_FILL).copy(alpha = p))
                )
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(x, y) }
                    .size(w, h)
                    .graphicsLayer { alpha = layerAlpha }
                    .clip(RoundedCornerShape(corner))
                    .background(lerp(btnFill, surface, p * p)),
            )
        }
    }
}

/**
 * 首屏 —— provision_startup_layout + StartupFragment + AnimHelper:
 * 权重 30%/90dp logo/40%(字标顶 + 圆钮底)/20%；
 * 入场 = startPageLogoAnim(logo 与字标同规格: scale 0.5→0.95 sinOut(440)→1.0 cubicOut(700),
 * alpha 延迟 60ms) + startPageBtnAnim(scale 0.9→1 + alpha, cubicOut(450ms) 延迟 1340ms)；
 * 2500ms displayOsAndoDelay 兜底强制可见可点。admission=首入放圆环(needAdmission(true))。
 */
@Composable
private fun SplashStep(
    glowActive: Boolean,
    blurGlass: Boolean,
    rootHeightPx: Float,
    admission: Boolean,
    entryAnim: Boolean,
    onButtonBounds: (Rect) -> Unit,
    buttonHidden: Boolean,
    onExpand: () -> Unit,
) {
    // 字标组中心相对屏幕高度 → uCircleYOffset(setCircleYOffsetWithView)
    var wordmarkFrac by remember { mutableStateOf(0.345f) }

    GlowCanvas(
        modifier = Modifier.fillMaxSize(),
        active = glowActive,
        admission = admission,
        circleCenterFrac = wordmarkFrac,
    )

    // —— 入场动画（lite 降级: 不播, 全部落位）——
    val logoScale = remember { Animatable(if (entryAnim && glowActive) 0.5f else 1f) }
    val logoAlpha = remember { Animatable(if (entryAnim && glowActive) 0f else 1f) }
    val btnScale = remember { Animatable(if (entryAnim && glowActive) 0.9f else 1f) }
    val btnAlpha = remember { Animatable(if (entryAnim && glowActive) 0f else 1f) }
    var buttonReady by remember { mutableStateOf(!entryAnim || !glowActive) }

    LaunchedEffect(Unit) {
        if (!entryAnim || !glowActive) return@LaunchedEffect
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(300, delayMillis = 60)) }
            launch {
                logoScale.animateTo(0.95f, tween(440, easing = SIN_OUT))
                logoScale.animateTo(1f, tween(700, easing = CUBIC_OUT))
            }
            launch {
                delay(BUTTON_IN_DELAY_MS)
                btnScale.animateTo(1f, tween(BUTTON_IN_DUR_MS, easing = CUBIC_OUT))
                btnAlpha.animateTo(1f, tween(BUTTON_IN_DUR_MS, easing = CUBIC_OUT))
                buttonReady = true
            }
        }
    }
    // displayOsAndoDelay: 2500ms 强制恢复, 防动画回调丢失导致按钮永久点不动
    LaunchedEffect(Unit) {
        delay(DISPLAY_OS_ANDO_MS)
        logoAlpha.snapTo(1f)
        logoScale.snapTo(1f)
        btnAlpha.snapTo(1f)
        btnScale.snapTo(1f)
        buttonReady = true
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(0.30f))

        // logo 90dp（与字标同一套 scale/alpha —— startPageLogoAnim 同时作用两个 view）
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
                .background(MJ_LOGO_RED),
            contentAlignment = Alignment.Center,
        ) {
            Text("MJ", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxWidth(),
        ) {
            // 字标(logo_image_wrapper): 顶对齐 + marginTop 20dp
            Text(
                text = "MJ 彩蛋",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .onGloballyPositioned {
                        val root = it.boundsInRoot()
                        if (root.height > 0f && rootHeightPx > 0f) {
                            wordmarkFrac = root.center.y / rootHeightPx
                        }
                    }
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    },
            )

            // 圆钮 70dp(next_layout 4.5dp padding), 顶点对齐区块底
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .graphicsLayer {
                            scaleX = btnScale.value
                            scaleY = btnScale.value
                            alpha = if (buttonHidden) 0f else btnAlpha.value
                        }
                        .clip(CircleShape)
                        .background(if (blurGlass) GLASS_GREY else BTN_LITE)
                        .onGloballyPositioned { onButtonBounds(it.boundsInRoot()) }
                        .clickable(enabled = buttonReady && !buttonHidden, onClick = onExpand),
                    contentAlignment = Alignment.Center,
                ) {
                    ArrowIcon()
                }
            }
        }

        Spacer(Modifier.weight(0.20f))
    }
}

/** provision_icon_arrow 31×22 viewport 路径逐点照抄, 落位 29×20dp。 */
@Composable
private fun ArrowIcon() {
    Canvas(Modifier.size(29.dp, 20.dp)) {
        val sx = size.width / 31f
        val sy = size.height / 22f
        val path = Path().apply {
            moveTo(29.7773f * sx, 11.9345f * sy)
            cubicTo(30.0173f * sx, 11.6945f * sy, 30.1371f * sx, 11.3798f * sy, 30.1366f * sx, 11.0651f * sy)
            cubicTo(30.1406f * sx, 10.746f * sy, 30.0209f * sx, 10.4256f * sy, 29.7774f * sx, 10.182f * sy)
            lineTo(20.9212f * sx, 1.3259f * sy)
            cubicTo(20.4421f * sx, 0.8468f * sy, 19.6652f * sx, 0.8468f * sy, 19.1861f * sx, 1.3259f * sy)
            cubicTo(18.707f * sx, 1.8051f * sy, 18.707f * sx, 2.5819f * sy, 19.1861f * sx, 3.0611f * sy)
            lineTo(25.7778f * sx, 9.6528f * sy)
            lineTo(1.6739f * sx, 9.6527f * sy)
            cubicTo(0.9532f * sx, 9.6527f * sy, 0.3689f * sx, 10.237f * sy, 0.3689f * sx, 10.9577f * sy)
            cubicTo(0.3689f * sx, 11.6785f * sy, 0.9532f * sx, 12.2627f * sy, 1.6739f * sx, 12.2627f * sy)
            lineTo(25.9788f * sx, 12.2627f * sy)
            lineTo(19.186f * sx, 19.0555f * sy)
            cubicTo(18.7069f * sx, 19.5346f * sy, 18.7069f * sx, 20.3115f * sy, 19.186f * sx, 20.7906f * sy)
            cubicTo(19.6652f * sx, 21.2698f * sy, 20.442f * sx, 21.2698f * sy, 20.9212f * sx, 20.7906f * sy)
            close()
        }
        drawPath(path, Color.White)
    }
}

/** provision_picker_btn_radio 64×64 蓝色对勾, 落位 24dp。 */
@Composable
private fun CheckIcon() {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 64f
        val path = Path().apply {
            moveTo(50.8171f * s, 22.1514f * s)
            cubicTo(52.0496f * s, 20.6624f * s, 51.8417f * s, 18.4561f * s, 50.3527f * s, 17.2235f * s)
            cubicTo(48.8636f * s, 15.991f * s, 46.6573f * s, 16.1989f * s, 45.4247f * s, 17.6879f * s)
            lineTo(26.9535f * s, 40.0031f * s)
            lineTo(17.4007f * s, 30.4502f * s)
            cubicTo(16.0338f * s, 29.0833f * s, 13.8177f * s, 29.0833f * s, 12.4509f * s, 30.4502f * s)
            cubicTo(11.0841f * s, 31.817f * s, 11.0841f * s, 34.0331f * s, 12.4509f * s, 35.3999f * s)
            lineTo(24.7077f * s, 47.6567f * s)
            cubicTo(25.7244f * s, 48.6734f * s, 27.2109f * s, 48.9338f * s, 28.4683f * s, 48.4381f * s)
            cubicTo(29.016f * s, 48.2302f * s, 29.519f * s, 47.8817f * s, 29.9192f * s, 47.3982f * s)
            close()
        }
        drawPath(path, CHECK_BLUE)
    }
}

/** 返回箭头 = ?android:homeAsUpIndicator(细尖角), 40dp。 */
@Composable
private fun BackIcon() {
    val color = MiuixTheme.colorScheme.onSurface
    Canvas(Modifier.size(40.dp)) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = h * 0.06f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        val path = Path().apply {
            moveTo(w * 0.62f, h * 0.24f)
            lineTo(w * 0.36f, h * 0.5f)
            lineTo(w * 0.62f, h * 0.76f)
        }
        drawPath(path, color, style = stroke)
    }
}

/**
 * 向导页外壳 —— provision_detail_layout + provision_actionbar + GroupButtons：
 * 顶部 40dp 返回钮(actionbar marginTop 50dp) → 70dp 居中预览图标 → 居中标题 32sp
 * (minHeight 42dp, 35dp 水平) → 居中副标题 14sp(tertiary) → 内容 → 底部「继续」
 * 50dp 圆角 16dp(max 336dp, 底距 44dp)。进页按钮 1000ms 后才可点(delayEnableButton)。
 * 无内容入场动画（centerPageAnim/endPageAnim 在源码中并无调用）。
 */
@Composable
private fun GuidePage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    nextEnabled: Boolean = true,
    onNext: () -> Unit,
    content: @Composable () -> Unit,
) {
    // delayEnableButton: 进页后 1000ms 按钮置灰, 防转场途中连点
    var enableDelayDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BUTTON_ENABLE_DELAY_MS)
        enableDelayDone = true
    }
    val canNext = nextEnabled && enableDelayDone

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        // actionbar: marginTop 50dp + paddingVertical 8dp
        Box(
            Modifier
                .padding(start = 20.dp, top = 8.dp)
                .size(40.dp)
                .clickable(onClick = onBack),
        ) { BackIcon() }

        Spacer(Modifier.height(36.dp))   // provision_space_between_actionbar_title

        // preview_image 70dp
        PreviewIcon(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 8.dp),
            glyph = title,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 35.dp, vertical = 0.dp),
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 32.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 42.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        content()

        Spacer(Modifier.weight(1f))

        ProvisionButton(
            label = "继续",
            enabled = canNext,
            onClick = onNext,
        )
    }
}

/** 页面预览图标(70dp): 蓝色描边圆角方块 + 单字。 */
@Composable
private fun PreviewIcon(modifier: Modifier = Modifier, glyph: String) {
    Box(
        modifier = modifier
            .size(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PROVISION_BLUE.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph.take(1),
            color = PROVISION_BLUE,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 底部主按钮 —— GroupButtons 主按钮观感(#3482FF, 50dp, 圆角 16dp, 白字 17sp,
 * max 336dp, 底距 44dp)。禁用 = HALF_ALPHA 0.5 且不可点(OobeUtils.HALF_ALPHA)。
 */
@Composable
private fun ProvisionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    dark: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 0.dp)
            .padding(bottom = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 336.dp)
                .fillMaxWidth()
                .height(50.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
                .clip(RoundedCornerShape(16.dp))
                .background(if (dark) Color(0x99000000) else PROVISION_BLUE)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 权限行 —— PermissionItemView: 56dp, 圆角列表底, 左标题, 右蓝 check(未勾=占位不显示)。 */
@Composable
private fun PermissionRow(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            if (checked) CheckIcon()
            else Spacer(Modifier.size(24.dp))
        }
    }
}

/**
 * 权限设置页 —— PermissionSettingsActivity/Fragment:
 * 单门控范式: 无障碍服务未连通时「继续」禁用 + alpha 0.5(setAllowNext), 通后自动亮起。
 * 状态 1s 轮询刷新。
 */
@Composable
private fun PermissionStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val a11y by produceState(initialValue = false to false) {
        while (true) {
            value = MjAccessibilityService.isConnected() to
                    MjAccessibilityService.isEnabledInSettings(context)
            delay(1000)
        }
    }
    val (connected, enabledInSettings) = a11y

    GuidePage(
        title = "权限设置",
        subtitle = "彩蛋靠无障碍服务只读监听聊天输入框的文本变化。\n全程只读，不会替你打字或发消息。",
        onBack = onBack,
        nextEnabled = connected || enabledInSettings,
        onNext = onNext,
    ) {
        Column(Modifier.fillMaxWidth()) {
            PermissionRow(
                title = "无障碍服务（必需）",
                checked = connected || enabledInSettings,
                onClick = { MjAccessibilityService.openAccessibilitySettings(context) },
            )
            Text(
                text = if (connected) "已连接，正在收事件。您可以稍后在「设置」中更改。"
                else "点按该行前往系统设置开启；开启后返回本页即可继续。",
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 0.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
            )
        }
    }
}

/** 基础设置页 —— BasicSettingsActivity/Fragment: 偏好列表, 无门控。 */
@Composable
private fun BasicStep(settings: SettingsStore, onBack: () -> Unit, onNext: () -> Unit) {
    GuidePage(
        title = "基础设置",
        subtitle = "选择要监控的聊天应用，随时可以在「功能」页改。",
        onBack = onBack,
        onNext = onNext,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
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
}

/**
 * 完成页 —— provision_congratulation_layout + CongratulationFragment:
 * 权重 18/40/0.2；辉光 start(false) 无圆环；logo 组(logo 90dp + 字标 20dp + 状态字 30dp)
 * 整体 translationY 100px→0 + alpha(quartOut 1500ms)；按钮 alpha sinOut(450ms) 延迟 1000ms,
 * 2000ms 后才可点(startBtnAnim postDelayed)。点「开始使用」: logo 组 + 按钮 scale 1→0.8
 * spring(1.0, 0.36) + alpha sinOut(360ms)（startPageAnim）→ 接力主页进场。
 */
@Composable
private fun DoneStep(glowActive: Boolean, blurGlass: Boolean, onDone: () -> Unit) {
    GlowCanvas(
        modifier = Modifier.fillMaxSize(),
        active = glowActive,
        admission = false,
        circleCenterFrac = 0.35f,
    )

    val entryAnim = glowActive
    val logoY = remember { Animatable(if (entryAnim) 100f else 0f) }
    val logoAlpha = remember { Animatable(if (entryAnim) 0f else 1f) }
    val btnAlpha = remember { Animatable(if (entryAnim) 0f else 1f) }
    val outScale = remember { Animatable(1f) }
    val outAlpha = remember { Animatable(1f) }
    var leaving by remember { mutableStateOf(false) }
    // startBtnAnim: postDelayed(2000) → setEnabled(true)
    var btnReady by remember { mutableStateOf(!entryAnim) }

    LaunchedEffect(Unit) {
        if (!entryAnim) return@LaunchedEffect
        coroutineScope {
            launch {
                logoY.animateTo(0f, tween(1500, easing = QUART_OUT))
                logoAlpha.animateTo(1f, tween(1500, easing = QUART_OUT))
            }
            launch {
                delay(1000)                       // startBtnAnim setDelay(1000)
                btnAlpha.animateTo(1f, tween(450, easing = SIN_OUT))
            }
            launch {
                delay(2000)                       // startBtnAnim postDelayed(2000) setEnabled
                btnReady = true
            }
        }
    }

    if (leaving) {
        LaunchedEffect(Unit) {
            coroutineScope {
                launch {
                    // FolmeEase.spring(1.0f, 0.36f) = 阻尼 1.0(临界) / 响应 0.36s
                    outScale.animateTo(0.8f, spring(dampingRatio = 1f, stiffness = 305f))
                }
                launch {
                    outAlpha.animateTo(0f, tween(360, easing = SIN_OUT))
                }
            }
            onDone()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(18f))

        Box(
            modifier = Modifier
                .weight(40f)
                .fillMaxWidth(),
        ) {
            // logo 组: 整组同一套入场/退场(startLogoAnim 作用在 wrapper 上)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = logoY.value          // Folme TRANSLATION_Y=100 原始像素
                        alpha = logoAlpha.value * outAlpha.value
                        scaleX = outScale.value
                        scaleY = outScale.value
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MJ_LOGO_RED),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("MJ", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "MJ 彩蛋",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
                Spacer(Modifier.height(30.dp))
                Text(
                    text = "设置完毕",
                    color = STATE_TEXT_COLOR,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // next_container: 底对齐 + marginBottom 44dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = btnAlpha.value * outAlpha.value
                        scaleX = outScale.value
                        scaleY = outScale.value
                    },
            ) {
                ProvisionButton(
                    label = "开始使用",
                    enabled = btnReady && !leaving,
                    onClick = { leaving = true },
                    dark = true,
                )
            }
        }

        Spacer(Modifier.weight(0.2f))
    }
}

/**
 * 辉光画布 —— GlowPainter uniform 全表 + GlowController tickPingPong + RenderViewLayout:
 * 0.2× 分辨率居中渲染 + scale 5× + 黑底(-16777216)、uTime 从 0 起 ping-pong 2↔120
 * (只翻向不夹取, 照抄 tickPingPong)、uCircleYOffset = 0.5 - 圆心比例(setCircleYOffsetWithView)。
 * admission=false → needAdmission(false) 仅流动背景。shader 不可用时退化静态渐变。
 */
@Composable
private fun GlowCanvas(
    modifier: Modifier,
    active: Boolean,
    admission: Boolean,
    circleCenterFrac: Float,
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
            setFloatUniform("uCircleYOffset", 0.1f)
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

    // GlowController: uTime 从 0 起步, ping-pong 2↔120（只翻向, 不夹取）
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var dir = 1f
        var last = System.nanoTime()
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val v = time.value + dir * dt
            if (dir > 0f && v >= 120f) dir = -1f
            else if (dir < 0f && v <= 2f) dir = 1f
            time.snapTo(v)
        }
    }

    Box(modifier) {
        // RenderViewLayout 同款策略: 0.2× 居中 + scale 5× + 黑底
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
                        s.setFloatUniform("uCircleYOffset", 0.5f - circleCenterFrac)
                        drawRect(ShaderBrush(s))
                    }
            )
        }
    }
}
