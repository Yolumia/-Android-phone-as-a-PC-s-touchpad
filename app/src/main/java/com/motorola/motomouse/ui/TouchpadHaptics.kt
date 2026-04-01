package com.motorola.motomouse.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import com.motorola.motomouse.data.TouchpadHapticSettings
import kotlin.math.roundToInt

enum class TouchpadHapticType {
    PrimaryClick,
    SecondaryClick,
    DragStart,
}

class TouchpadHapticFeedbackController(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @SuppressLint("MissingPermission")
    fun perform(type: TouchpadHapticType, settings: TouchpadHapticSettings) {
        if (!settings.enabled) return
        val activeVibrator = vibrator ?: return
        if (!activeVibrator.hasVibrator()) return

        val intensity = settings.intensity.coerceIn(0f, 1f)
        val frequency = settings.frequency.coerceIn(0f, 1f)
        val baseScale = (0.18f + intensity * 0.82f).coerceIn(0.1f, 1f)
        val gapMs = (22 - frequency * 13f).roundToInt().coerceIn(7, 22)
        val baseDurationMs = when (type) {
            TouchpadHapticType.PrimaryClick -> 14L
            TouchpadHapticType.SecondaryClick -> 18L
            TouchpadHapticType.DragStart -> 11L
        }
        val amplitude = (45 + intensity * 210f).roundToInt().coerceIn(1, 255)
        val accentAmplitude = (amplitude * when (type) {
            TouchpadHapticType.PrimaryClick -> 0.58f
            TouchpadHapticType.SecondaryClick -> 0.72f
            TouchpadHapticType.DragStart -> 0.48f
        }).roundToInt().coerceIn(1, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val primitive = when (type) {
                TouchpadHapticType.PrimaryClick -> VibrationEffect.Composition.PRIMITIVE_CLICK
                TouchpadHapticType.SecondaryClick -> VibrationEffect.Composition.PRIMITIVE_CLICK
                TouchpadHapticType.DragStart -> VibrationEffect.Composition.PRIMITIVE_TICK
            }
            val accentPrimitive = when (type) {
                TouchpadHapticType.PrimaryClick -> VibrationEffect.Composition.PRIMITIVE_TICK
                TouchpadHapticType.SecondaryClick -> VibrationEffect.Composition.PRIMITIVE_CLICK
                TouchpadHapticType.DragStart -> VibrationEffect.Composition.PRIMITIVE_TICK
            }
            if (activeVibrator.areAllPrimitivesSupported(primitive, accentPrimitive)) {
                val effect = VibrationEffect.startComposition().apply {
                    addPrimitive(primitive, baseScale)
                    if (type != TouchpadHapticType.DragStart || frequency > 0.35f) {
                        addPrimitive(accentPrimitive, (baseScale * 0.78f).coerceAtLeast(0.1f), gapMs)
                    }
                }.compose()
                activeVibrator.vibrate(effect)
                return
            }
        }

        val effect = when (type) {
            TouchpadHapticType.DragStart -> {
                if (activeVibrator.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(baseDurationMs, amplitude)
                } else {
                    VibrationEffect.createOneShot(baseDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
            }

            else -> {
                val timings = longArrayOf(0L, baseDurationMs, gapMs.toLong(), (baseDurationMs * 0.72f).roundToInt().toLong())
                val amplitudes = if (activeVibrator.hasAmplitudeControl()) {
                    intArrayOf(0, amplitude, 0, accentAmplitude)
                } else {
                    intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            }
        }
        activeVibrator.vibrate(effect)
    }
}

@Composable
fun rememberTouchpadHapticController(): TouchpadHapticFeedbackController {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        TouchpadHapticFeedbackController(context)
    }
}

