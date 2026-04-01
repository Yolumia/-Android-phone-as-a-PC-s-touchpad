package com.motorola.motomouse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.motorola.motomouse.data.TouchpadHapticSettings
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MotoMouseRepository.getInstance(application.applicationContext)

    val uiState: StateFlow<MainUiState> = repository.uiState
    val touchpadSettings: StateFlow<TouchpadHapticSettings> = repository.touchpadSettings

    fun onQrScanned(rawValue: String) = repository.onQrScanned(rawValue)

    fun clearPairing() = repository.clearPairing()

    fun retryConnection() = repository.retryConnection()

    fun sendPointerMove(dx: Float, dy: Float) = repository.sendPointerMove(dx, dy)

    fun sendLeftClick() = repository.sendLeftClick()

    fun sendRightClick() = repository.sendRightClick()

    fun sendDragStart() = repository.sendDragStart()

    fun sendDragMove(dx: Float, dy: Float) = repository.sendDragMove(dx, dy)

    fun sendDragEnd() = repository.sendDragEnd()

    fun sendScroll(dx: Int, dy: Int) = repository.sendScroll(dx, dy)

    fun sendZoom(steps: Int) = repository.sendZoom(steps)

    fun sendGesture(name: String) = repository.sendGesture(name)

    fun setHapticsEnabled(enabled: Boolean) = repository.setHapticsEnabled(enabled)

    fun setHapticIntensity(intensity: Float) = repository.setHapticIntensity(intensity)

    fun setHapticFrequency(frequency: Float) = repository.setHapticFrequency(frequency)
}

