package com.lxithral.mjegg.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题色预设色板。
 *
 * 第 0 项 `keyColor == 0` 是特殊值: 交给 miuix ThemeController 走"系统壁纸取色",
 * 其余项作为 Monet 种子色使用。
 */
data class PaletteEntry(val name: String, val argb: Int)

val ThemePalette: List<PaletteEntry> = listOf(
    PaletteEntry("壁纸", 0),                       // 0 = 跟随系统壁纸
    PaletteEntry("蓝", 0xFF3482FF.toInt()),
    PaletteEntry("靛", 0xFF4C5BD4.toInt()),
    PaletteEntry("紫", 0xFF8B5CF6.toInt()),
    PaletteEntry("品红", 0xFFD946A6.toInt()),
    PaletteEntry("玫红", 0xFFE0455C.toInt()),
    PaletteEntry("朱红", 0xFFE63C3C.toInt()),
    PaletteEntry("橙", 0xFFF07C22.toInt()),
    PaletteEntry("琥珀", 0xFFE0A020.toInt()),
    PaletteEntry("黄绿", 0xFF8CB820.toInt()),
    PaletteEntry("绿", 0xFF22A559.toInt()),
    PaletteEntry("青", 0xFF00A6A6.toInt()),
    PaletteEntry("天青", 0xFF00A0C6.toInt()),
    PaletteEntry("棕", 0xFF9A6B4F.toInt()),
    PaletteEntry("灰蓝", 0xFF5A6B85.toInt()),
)

/** 按 ARGB 找预设名, 找不到就显示十六进制。 */
fun paletteNameOf(argb: Int): String =
    ThemePalette.firstOrNull { it.argb == argb }?.name
        ?: "#%06X".format(argb and 0xFFFFFF)
