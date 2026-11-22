package tw.knox.dailytasks.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import tw.knox.dailytasks.data.TaskPriority
import java.time.LocalDate

enum class ThemeMode { LIGHT, DARK, SYSTEM }

internal val LocalTaskToday = compositionLocalOf { LocalDate.now() }

val BrandBlue = Color(0xFF2378EC)
val BrandBlueDark = Color(0xFF145EBD)
val Ink = Color(0xFF17233A)
val Muted = Color(0xFF65738B)
val Canvas = Color(0xFFF8FAFF)
val High = Color(0xFFEE3238)
val HighSoft = Color(0xFFFFE6E8)
val Medium = Color(0xFF1681E8)
val MediumSoft = Color(0xFFDDEEFF)
val Low = Color(0xFF1E9A4D)
val LowSoft = Color(0xFFDDF5E3)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Ink,
    secondary = Low,
    error = High,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F4FA),
    onSurfaceVariant = Muted,
    outline = Color(0xFFD6DFEC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3F86E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF164A84),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF2E9B59),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E4B31),
    onSecondaryContainer = Color.White,
    error = Color(0xFFD94B52),
    onError = Color.White,
    errorContainer = Color(0xFF5A2227),
    onErrorContainer = Color.White,
    background = Color(0xFF10151D),
    onBackground = Color.White,
    surface = Color(0xFF171D26),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF232B37),
    onSurfaceVariant = Color.White,
    outline = Color(0xFF414C5D)
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    titleSmall = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 25.sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 29.sp),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 33.sp),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp)
)

@Composable
fun DailyTasksTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var today by remember { mutableStateOf(LocalDate.now()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { today = LocalDate.now() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        today = LocalDate.now()
        onDispose { context.unregisterReceiver(receiver) }
    }
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    CompositionLocalProvider(LocalTaskToday provides today) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}

@Composable
fun isDarkAppTheme(): Boolean = MaterialTheme.colorScheme.background == DarkColors.background

@Composable
fun priorityContainer(priority: TaskPriority): Color {
    val dark = MaterialTheme.colorScheme.background == DarkColors.background
    return if (dark) {
        when (priority) {
            TaskPriority.HIGH -> Color(0xFF52282D)
            TaskPriority.MEDIUM -> Color(0xFF1E3A55)
            TaskPriority.LOW -> Color(0xFF203F2D)
        }
    } else when (priority) {
        TaskPriority.HIGH -> HighSoft
        TaskPriority.MEDIUM -> MediumSoft
        TaskPriority.LOW -> LowSoft
    }
}

fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> High
    TaskPriority.MEDIUM -> Medium
    TaskPriority.LOW -> Low
}
