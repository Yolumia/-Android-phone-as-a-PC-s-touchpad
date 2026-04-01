package com.motorola.motomouse.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.motorola.motomouse.data.TouchpadHapticSettings
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@Composable
fun PairingScreen(
    statusMessage: String,
    pairedServerName: String?,
    hasSavedPairing: Boolean,
    hapticSettings: TouchpadHapticSettings,
    onQrScanned: (String) -> Unit,
    onRetryConnection: () -> Unit,
    onForgetPairing: () -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onHapticIntensityChange: (Float) -> Unit,
    onHapticFrequencyChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var intensityDraft by remember(hapticSettings.intensity) {
        mutableFloatStateOf(hapticSettings.intensity)
    }
    var frequencyDraft by remember(hapticSettings.frequency) {
        mutableFloatStateOf(hapticSettings.frequency)
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Moto Mouse",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "扫码连接 Windows 电脑",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "1. 在电脑上运行本项目里的 Python 桌面端程序。\n2. 电脑端会弹出二维码。\n3. 手机上授权摄像头后扫描二维码即可建立 UDP 配对。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (hasSavedPairing) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "当前已保存配对",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = pairedServerName ?: "Windows PC",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "首页只负责扫码和配对。连接成功后会自动进入独立触摸板页面；如果断链，会自动回到这里。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onRetryConnection) {
                            Text("重新连接")
                        }
                        OutlinedButton(onClick = onForgetPairing) {
                            Text("清除配对")
                        }
                    }
                }
            }
        }

        if (hasCameraPermission) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "扫码区域",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    QrCameraPreview(
                        onQrScanned = onQrScanned,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                }
            }
        } else {
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "需要摄像头权限才能识别二维码。",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                    ) {
                        Text("授权摄像头")
                    }
                }
            }
        }

        OutlinedCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "连接说明",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "• 配对信息会保存在本机，下次启动会自动重连。\n• 断连后 App 会自动尝试重连 10 次。\n• 超过 10 次仍失败时，会清除旧配对并提示重新扫码。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        ElevatedCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "触觉反馈",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "触摸板点击反馈的开关、强度和频率在首页统一调节。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = hapticSettings.enabled,
                        onCheckedChange = onHapticsEnabledChange,
                    )
                }

                if (hapticSettings.enabled) {
                    Text(
                        text = "强度：${toPercent(intensityDraft)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = intensityDraft,
                        onValueChange = { intensityDraft = it },
                        onValueChangeFinished = {
                            onHapticIntensityChange(intensityDraft)
                        },
                    )

                    Text(
                        text = "频率：${frequencyLabel(frequencyDraft)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = frequencyDraft,
                        onValueChange = { frequencyDraft = it },
                        onValueChangeFinished = {
                            onHapticFrequencyChange(frequencyDraft)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QrCameraPreview(
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var didScan by remember { mutableStateOf(false) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    LaunchedEffect(didScan) {
        if (didScan) {
            delay(QR_SCAN_COOLDOWN_MS)
            didScan = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { androidContext ->
                PreviewView(androidContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(options)
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null || didScan) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val qrText = barcodes.firstOrNull()?.rawValue
                                    if (!qrText.isNullOrBlank() && !didScan) {
                                        didScan = true
                                        onQrScanned(qrText)
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }
                    }

                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer,
                        )
                    },
                    ContextCompat.getMainExecutor(context),
                )
            },
        )

        if (!didScan) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = 28.dp)),
            ) {
                Text(
                    text = "将电脑端二维码置于取景框内",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private const val QR_SCAN_COOLDOWN_MS = 1_500L

private fun toPercent(value: Float): String {
    return "${(value.coerceIn(0f, 1f) * 100).toInt()}%"
}

private fun frequencyLabel(value: Float): String {
    return when {
        value < 0.33f -> "柔和"
        value < 0.66f -> "平衡"
        else -> "紧致"
    }
}

