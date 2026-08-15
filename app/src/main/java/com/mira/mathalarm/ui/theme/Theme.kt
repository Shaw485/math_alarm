package com.mira.mathalarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * App 主题
 * 本 App 仅使用深色主题（纯黑背景 + 橙色主色），不做浅色模式
 */
private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = AppColors.Primary,
    onSecondary = Color.White,
    tertiary = AppColors.Success,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceLight,
    onSurfaceVariant = AppColors.TextSecondary,
    error = AppColors.Error,
    onError = Color.White,
)

@Composable
fun MathAlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
