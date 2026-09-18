package com.lxithral.mjegg.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 状态卡三态。 */
enum class StatusKind { WORKING, ERROR, LOADING }

/**
 * 主页「大方块状态卡」—— 参照 KernelSU / Mishka 的构图语言:
 * 近正方大卡 + 左上状态图标与标题 + 右下巨型水印图标(出血、低透明度)。
 */
@Composable
fun StatusCard(
    kind: StatusKind,
    title: String,
    summary: String,
    tag: String? = null,
    isDark: Boolean,
    watermark: ImageVector,
    modifier: Modifier = Modifier,
) {
    val accent = when (kind) {
        StatusKind.WORKING -> Color(0xFF36D167)
        StatusKind.ERROR -> Color(0xFFE0453F)
        StatusKind.LOADING -> MiuixTheme.colorScheme.primary
    }
    val container = when (kind) {
        StatusKind.WORKING -> if (isDark) Color(0xFF1A3825) else Color(0xFFDFFAE4)
        StatusKind.ERROR -> if (isDark) Color(0xFF3B1E1C) else Color(0xFFFFE5E3)
        StatusKind.LOADING -> MiuixTheme.colorScheme.surfaceContainer
    }
    val statusIcon = when (kind) {
        StatusKind.WORKING -> MiuixIcons.Ok
        StatusKind.ERROR -> MiuixIcons.Report
        StatusKind.LOADING -> MiuixIcons.Refresh
    }
    val onColor = if (isDark) Color(0xFFF2F3F5) else Color(0xFF1A1A1A)

    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(container)
        ) {
            // 右下角巨型水印: 朝右下出血, 被卡片裁切
            Icon(
                imageVector = watermark,
                contentDescription = null,
                tint = accent.copy(alpha = if (isDark) 0.22f else 0.18f),
                modifier = Modifier
                    .size(168.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 34.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = onColor,
                        fontSize = MiuixTheme.textStyles.title2.fontSize,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summary,
                    color = onColor.copy(alpha = 0.72f),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
            }

            if (tag != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = tag,
                        color = accent,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
