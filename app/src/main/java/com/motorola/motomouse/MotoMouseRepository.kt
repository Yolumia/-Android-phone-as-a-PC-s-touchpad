package com.motorola.motomouse

import android.content.Context
import com.motorola.motomouse.data.PairingInfo
import com.motorola.motomouse.data.PairingStore
import com.motorola.motomouse.data.TouchpadHapticSettings
import com.motorola.motomouse.data.TouchpadSettingsStore
import com.motorola.motomouse.network.ConnectionState
import com.motorola.motomouse.network.UdpRemoteTouchClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val statusMessage: String = "正在初始化",
    val pairingInfo: PairingInfo? = null,
    val serverName: String = "",
    val isConnected: Boolean = false,
    val reconnectMessage: String? = null,
)

class MotoMouseRepository private constructor(appContext: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pairingStore = PairingStore(appContext)
    private val settingsStore = TouchpadSettingsStore(appContext)
    private val remoteTouchClient = UdpRemoteTouchClient(
        parentScope = applicationScope,
        onRepairRequired = { reason ->
            pairingStore.clear()
            currentPairing = null
            state.value = MainUiState(statusMessage = reason)
        },
    )

    private val state = MutableStateFlow(MainUiState())
    private var currentPairing: PairingInfo? = null

    val uiState: StateFlow<MainUiState> = state.asStateFlow()
    val touchpadSettings: StateFlow<TouchpadHapticSettings> = settingsStore.settings.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = TouchpadHapticSettings(),
    )

    init {
        observeConnectionState()
        restoreSavedPairing()
    }

    fun onQrScanned(rawValue: String) {
        PairingInfo.fromQrPayload(rawValue)
            .onSuccess { pairingInfo ->
                currentPairing = pairingInfo
                state.update {
                    it.copy(
                        pairingInfo = pairingInfo,
                        serverName = pairingInfo.serverName,
                        statusMessage = "二维码已识别，正在连接 ${pairingInfo.serverName}",
                        isConnected = false,
                        reconnectMessage = null,
                    )
                }
                applicationScope.launch {
                    pairingStore.save(pairingInfo)
                    remoteTouchClient.connect(pairingInfo)
                }
            }
            .onFailure { error ->
                state.update {
                    it.copy(
                        statusMessage = error.message ?: "二维码内容无效，请重新扫码。",
                        isConnected = false,
                        reconnectMessage = null,
                    )
                }
            }
    }

    fun clearPairing() {
        currentPairing = null
        remoteTouchClient.clearPairing()
        applicationScope.launch {
            pairingStore.clear()
            state.value = MainUiState(statusMessage = "请扫描电脑端二维码完成配对。")
        }
    }

    fun retryConnection() {
        currentPairing?.let(remoteTouchClient::connect) ?: restoreSavedPairing()
    }

    fun sendPointerMove(dx: Float, dy: Float) = remoteTouchClient.sendPointerMove(dx, dy)

    fun sendLeftClick() = remoteTouchClient.sendLeftClick()

    fun sendRightClick() = remoteTouchClient.sendRightClick()

    fun sendDragStart() = remoteTouchClient.sendDragStart()

    fun sendDragMove(dx: Float, dy: Float) = remoteTouchClient.sendDragMove(dx, dy)

    fun sendDragEnd() = remoteTouchClient.sendDragEnd()

    fun sendScroll(dx: Int, dy: Int) = remoteTouchClient.sendScroll(dx, dy)

    fun sendZoom(steps: Int) = remoteTouchClient.sendZoom(steps)

    fun sendGesture(name: String) = remoteTouchClient.sendGesture(name)

    fun setHapticsEnabled(enabled: Boolean) {
        applicationScope.launch {
            settingsStore.updateEnabled(enabled)
        }
    }

    fun setHapticIntensity(intensity: Float) {
        applicationScope.launch {
            settingsStore.updateIntensity(intensity)
        }
    }

    fun setHapticFrequency(frequency: Float) {
        applicationScope.launch {
            settingsStore.updateFrequency(frequency)
        }
    }

    private fun observeConnectionState() {
        applicationScope.launch {
            remoteTouchClient.connectionState.collect { connectionState ->
                when (connectionState) {
                    ConnectionState.Idle -> {
                        if (currentPairing == null) {
                            state.value = MainUiState(statusMessage = "请扫描电脑端二维码完成配对。")
                        }
                    }

                    is ConnectionState.Connecting -> {
                        state.update {
                            it.copy(
                                pairingInfo = currentPairing,
                                serverName = currentPairing?.serverName.orEmpty(),
                                statusMessage = connectionState.message,
                                isConnected = false,
                                reconnectMessage = null,
                            )
                        }
                    }

                    is ConnectionState.Connected -> {
                        state.update {
                            it.copy(
                                pairingInfo = currentPairing,
                                serverName = connectionState.serverName,
                                statusMessage = "已连接到 ${connectionState.serverName}",
                                isConnected = true,
                                reconnectMessage = null,
                            )
                        }
                    }

                    is ConnectionState.Reconnecting -> {
                        state.update {
                            it.copy(
                                pairingInfo = currentPairing,
                                serverName = currentPairing?.serverName.orEmpty(),
                                statusMessage = connectionState.message,
                                isConnected = false,
                                reconnectMessage = "自动重连中（${connectionState.attempt}/${connectionState.maxAttempts}）",
                            )
                        }
                    }

                    is ConnectionState.Error -> {
                        state.update {
                            it.copy(
                                pairingInfo = currentPairing,
                                serverName = currentPairing?.serverName.orEmpty(),
                                statusMessage = connectionState.message,
                                isConnected = false,
                                reconnectMessage = null,
                            )
                        }
                    }

                    is ConnectionState.RepairRequired -> {
                        currentPairing = null
                        state.value = MainUiState(statusMessage = connectionState.message)
                    }
                }
            }
        }
    }

    private fun restoreSavedPairing() {
        applicationScope.launch {
            val pairingInfo = pairingStore.load()
            currentPairing = pairingInfo
            if (pairingInfo == null) {
                state.value = MainUiState(statusMessage = "请扫描电脑端二维码完成配对。")
            } else {
                state.value = MainUiState(
                    pairingInfo = pairingInfo,
                    serverName = pairingInfo.serverName,
                    statusMessage = "发现已保存配对，正在连接 ${pairingInfo.serverName}",
                    isConnected = false,
                )
                remoteTouchClient.connect(pairingInfo)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: MotoMouseRepository? = null

        fun getInstance(context: Context): MotoMouseRepository {
            return instance ?: synchronized(this) {
                instance ?: MotoMouseRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}

