package ci.nsu.mobile.main.ui.screens

import android.Manifest
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import ci.nsu.mobile.domain.models.QrAuthData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onQrScanned: (login: String, password: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var cameraInitialized by remember { mutableStateOf(false) }
    var qrCodeText by remember { mutableStateOf<String?>(null) }
    var timeLeft by remember { mutableStateOf(30) }
    var isScanningActive by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Таймер
    LaunchedEffect(isScanningActive) {
        if (isScanningActive) {
            while (timeLeft > 0 && isScanningActive) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0 && isScanningActive) {
                isScanningActive = false
                errorMessage = "Время сканирования истекло"
            }
        }
    }

    // Запрос разрешения
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Сканирование QR-кода") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Text("←", fontSize = 24.sp)
                }
            },
            actions = {
                Text("Осталось: ${timeLeft}c", modifier = Modifier.padding(16.dp))
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !cameraPermissionState.status.isGranted -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Нет доступа к камере", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                            Text("Запросить разрешение")
                        }
                    }
                }
                errorMessage != null -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Назад") }
                    }
                }
                else -> {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        if (isScanningActive && qrCodeText == null) {
                                            val qrResult = decodeQrFromImageProxy(imageProxy)
                                            if (qrResult != null) {
                                                qrCodeText = qrResult
                                                isScanningActive = false
                                                val qrData = QrAuthData.fromJson(qrResult)
                                                if (qrData != null) {
                                                    scope.launch {
                                                        onQrScanned(qrData.login, qrData.password)
                                                    }
                                                } else {
                                                    errorMessage = "Неверный формат QR-кода"
                                                }
                                            }
                                        }
                                        imageProxy.close()
                                    }

                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                    cameraInitialized = true
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!cameraInitialized) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    // Рамка для наведения
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(250.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        ) {}
                    }
                }
            }
        }
    }
}

private fun decodeQrFromImageProxy(imageProxy: ImageProxy): String? {
    val buffer = imageProxy.planes[0].buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)

    val imageFormat = imageProxy.format
    if (imageFormat != android.graphics.ImageFormat.YUV_420_888) return null

    val source = PlanarYUVLuminanceSource(
        data, imageProxy.width, imageProxy.height,
        0, 0, imageProxy.width, imageProxy.height, false
    )
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to setOf(BarcodeFormat.QR_CODE))

    return try {
        MultiFormatReader().decode(binaryBitmap, hints).text
    } catch (e: Exception) {
        Log.e("QrScan", "Ошибка: ${e.message}")
        null
    }
}