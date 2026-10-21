package com.nest.chat.ui

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nest.chat.AppViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

object QrCodes {

    const val PREFIX = "nest://user/"

    fun bitmap(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    fun share(context: Context, bitmap: Bitmap, caption: String) {
        val dir = File(context.cacheDir, "qr")
        dir.mkdirs()
        val file = File(dir, "nest_qr.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
        send.type = "image/png"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.putExtra(Intent.EXTRA_TEXT, caption)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "Chia sẻ mã QR")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun save(context: Context, bitmap: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "nest_qr_${System.currentTimeMillis()}.png")
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nest")
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun QrScanScreen(vm: AppViewModel) {
    val c = Nest.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var torch by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var handled by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
    }

    fun handle(raw: String) {
        if (handled) return
        if (raw.startsWith(QrCodes.PREFIX)) {
            handled = true
            vm.scannedUid = raw.removePrefix(QrCodes.PREFIX)
            vm.back()
        } else {
            vm.toast("Đây không phải mã QR của Nest")
        }
    }

    val handleState = rememberUpdatedState({ raw: String -> handle(raw) })

    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
            scanner.close()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        val raw = codes.firstOrNull { it.rawValue != null }?.rawValue
                        if (raw == null) vm.toast("Không tìm thấy mã QR trong ảnh") else handle(raw)
                    }
                    .addOnFailureListener { vm.toast("Không đọc được ảnh") }
            } catch (e: Exception) {
                vm.toast("Không đọc được ảnh")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }
    LaunchedEffect(zoom, camera) { camera?.cameraControl?.setLinearZoom(zoom) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Quét mã QR", onBack = { vm.back() })
        if (granted) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val cameraProvider = future.get()
                            provider = cameraProvider
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { proxy ->
                                val bitmap = proxy.toBitmap()
                                val input = InputImage.fromBitmap(bitmap, proxy.imageInfo.rotationDegrees)
                                scanner.process(input)
                                    .addOnSuccessListener { codes ->
                                        val raw = codes.firstOrNull { it.rawValue != null }?.rawValue
                                        if (raw != null) handleState.value(raw)
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(240.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                )
            }
            Slider(
                value = zoom,
                onValueChange = { zoom = it },
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = c.accent,
                    activeTrackColor = c.accent,
                    inactiveTrackColor = c.glassStrong
                )
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Label(
                    "Cần quyền camera để quét mã QR. Bạn vẫn có thể chọn một ảnh có mã QR từ thư viện.",
                    color = c.textSoft,
                    align = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                NestButton("Cấp quyền camera", { permissionLauncher.launch(Manifest.permission.CAMERA) })
                Spacer(Modifier.height(8.dp))
                NestButton(
                    "Mở cài đặt ứng dụng",
                    {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        context.startActivity(intent)
                    },
                    primary = false
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (granted) {
                NestButton(
                    if (torch) "Tắt đèn" else "Bật đèn",
                    { torch = !torch },
                    Modifier.weight(1f),
                    primary = false
                )
            }
            NestButton(
                "Chọn ảnh có mã QR",
                { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(84.dp))
    }
}
