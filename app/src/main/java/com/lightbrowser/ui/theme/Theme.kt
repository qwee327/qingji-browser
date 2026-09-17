package com.lightbrowser.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 多彩主题预设：选择后覆盖整套界面主色（Android 12+ 开启动态取色时以壁纸色为准） */
data class ThemePreset(
    val id: String,
    val label: String,
    val primaryLight: Color,
    val onPrimaryLight: Color,
    val primaryDark: Color
)

val ThemePresets = listOf(
    ThemePreset("classic", "经典蓝", Color(0xFF1B5FD9), Color(0xFFFFFFFF), Color(0xFFB1C5FF)),
    ThemePreset("cherry", "樱桃红", Color(0xFFD81B60), Color(0xFFFFFFFF), Color(0xFFF48FB1)),
    ThemePreset("coral", "珊瑚橙", Color(0xFFF4511E), Color(0xFFFFFFFF), Color(0xFFFFAB91)),
    ThemePreset("lemon", "柠檬黄", Color(0xFFF9A825), Color(0xFF3A2A00), Color(0xFFFFE082)),
    ThemePreset("emerald", "翡翠绿", Color(0xFF00897B), Color(0xFFFFFFFF), Color(0xFF80CBC4)),
    ThemePreset("grape", "葡萄紫", Color(0xFF8E24AA), Color(0xFFFFFFFF), Color(0xFFCE93D8))
)

fun themePresetById(id: String): ThemePreset =
    ThemePresets.firstOrNull { it.id == id } ?: ThemePresets.first()

/** 向白色混合，得到更亮的色调 */
private fun Color.lighten(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = 1f
)

/** 向黑色混合，得到更暗的色调 */
private fun Color.darken(fraction: Float): Color = Color(
    red = red * (1f - fraction),
    green = green * (1f - fraction),
    blue = blue * (1f - fraction),
    alpha = 1f
)

private fun presetLightScheme(p: ThemePreset) = lightColorScheme(
    primary = p.primaryLight,
    onPrimary = p.onPrimaryLight,
    primaryContainer = p.primaryLight.lighten(0.82f),
    onPrimaryContainer = p.primaryLight.darken(0.55f),
    secondary = Color(0xFF565E71),
    secondaryContainer = Color(0xFFDAE2F9),
    tertiary = p.primaryLight.darken(0.15f),
    tertiaryContainer = p.primaryLight.lighten(0.9f)
)

private fun presetDarkScheme(p: ThemePreset) = darkColorScheme(
    primary = p.primaryDark,
    onPrimary = p.primaryLight.darken(0.7f),
    primaryContainer = p.primaryLight.darken(0.4f),
    onPrimaryContainer = p.primaryLight.lighten(0.85f),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF3E4759),
    tertiary = p.primaryDark.lighten(0.1f),
    tertiaryContainer = p.primaryLight.darken(0.25f)
)

/**
 * 轻级浏览器主题：
 * - Android 12+ 支持 Material You 动态取色
 * - 未开启动态取色（或系统不支持）时使用多彩主题预设
 * - 状态栏/导航栏图标颜色随主题自动切换，配合边到边显示
 */
@Composable
fun LightBrowserTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    presetId: String = "classic",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> presetDarkScheme(themePresetById(presetId))
        else -> presetLightScheme(themePresetById(presetId))
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
