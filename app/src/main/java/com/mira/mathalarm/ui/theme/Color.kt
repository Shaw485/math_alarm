package com.mira.mathalarm.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 全局颜色定义
 * 严格按照 PRD 设计稿：深色主题，纯黑背景，橙色主色调
 */
object AppColors {
    // 背景
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceLight = Color(0xFF2C2C2E)

    // 文字
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextTertiary = Color(0xFF6B7280)

    // 主色 - 橙色
    val Primary = Color(0xFFFF9500)
    val PrimaryDark = Color(0xFFE88600)

    // 状态色
    val Success = Color(0xFF22C55E)
    val Error = Color(0xFFEF4444)
    val Warning = Color(0xFFFF9500)

    // 分隔线
    val Divider = Color(0xFF3A3A3C)

    // 时间滚轮
    val WheelBackground = Color(0xFF1C1C1E)
    val WheelSelectedItem = Color(0xFFFFFFFF)
    val WheelUnselectedItem = Color(0xFF6B7280)

    // 弹窗遮罩
    val DimBackground = Color(0x80000000)
}
