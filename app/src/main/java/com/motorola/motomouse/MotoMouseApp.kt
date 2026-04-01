package com.motorola.motomouse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motorola.motomouse.ui.PairingScreen
import com.motorola.motomouse.ui.TouchpadScreen
import com.motorola.motomouse.ui.theme.MotoMouseTheme

@Composable
fun MotoMouseApp(
    viewModel: MainViewModel = viewModel(),
    onOpenTouchpad: () -> Unit = {},
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val hapticSettings = viewModel.touchpadSettings.collectAsStateWithLifecycle().value

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            onOpenTouchpad()
        }
    }

    MotoMouseTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            PairingScreen(
                statusMessage = uiState.reconnectMessage ?: uiState.statusMessage,
                pairedServerName = uiState.pairingInfo?.serverName,
                hasSavedPairing = uiState.pairingInfo != null,
                hapticSettings = hapticSettings,
                onQrScanned = viewModel::onQrScanned,
                onRetryConnection = viewModel::retryConnection,
                onForgetPairing = viewModel::clearPairing,
                onHapticsEnabledChange = viewModel::setHapticsEnabled,
                onHapticIntensityChange = viewModel::setHapticIntensity,
                onHapticFrequencyChange = viewModel::setHapticFrequency,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
fun TouchpadApp(
    viewModel: MainViewModel = viewModel(),
    onReturnHome: () -> Unit = {},
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(uiState.isConnected, uiState.pairingInfo) {
        if (!uiState.isConnected || uiState.pairingInfo == null) {
            onReturnHome()
        }
    }

    MotoMouseTheme {
        TouchpadScreen(
            hapticSettings = viewModel.touchpadSettings.collectAsStateWithLifecycle().value,
            onPointerMove = viewModel::sendPointerMove,
            onLeftClick = viewModel::sendLeftClick,
            onRightClick = viewModel::sendRightClick,
            onDragStart = viewModel::sendDragStart,
            onDragMove = viewModel::sendDragMove,
            onDragEnd = viewModel::sendDragEnd,
            onScroll = viewModel::sendScroll,
            onZoom = viewModel::sendZoom,
            onGesture = viewModel::sendGesture,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

