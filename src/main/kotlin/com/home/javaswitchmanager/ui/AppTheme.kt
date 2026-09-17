package com.home.javaswitchmanager.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val Background = Color(0xFF0F141B)
    val Surface = Color(0xFF151D27)
    val SurfaceAlt = Color(0xFF1A2532)
    val Card = Color(0xFF182432)
    val CardSelected = Color(0xFF163B56)
    val Border = Color(0xFF2D3B4D)
    val Accent = Color(0xFF55C8F3)
    val AccentStrong = Color(0xFF2594D0)
    val TextPrimary = Color(0xFFF3F6FB)
    val TextSecondary = Color(0xFF97A8BB)
    val Success = Color(0xFF74D79B)
    val Warning = Color(0xFFF1C36A)
    val Error = Color(0xFFFF8A8A)
}

private val colors = darkColors(
    primary = AppColors.Accent,
    primaryVariant = AppColors.AccentStrong,
    secondary = AppColors.Accent,
    background = AppColors.Background,
    surface = AppColors.Surface,
    onPrimary = Color(0xFF07131D),
    onSecondary = Color(0xFF07131D),
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary,
    error = AppColors.Error,
)

@Composable
fun JavaSwitchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = colors, content = content)
}
