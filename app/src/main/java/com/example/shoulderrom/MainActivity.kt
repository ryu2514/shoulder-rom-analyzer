package com.example.shoulderrom

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.os.Environment
import android.widget.TextView
import android.widget.Toast
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.example.shoulderrom.ui.PoseOverlayView
import com.example.shoulderrom.video.VideoOverlay
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import com.example.shoulderrom.model.Mode
import com.example.shoulderrom.model.Side
import com.example.shoulderrom.model.AngleCalculator

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var angleText: TextView
    private lateinit var statusText: TextView
    private lateinit var sideGroup: MaterialButtonToggleGroup
    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var btnResetPeak: com.google.android.material.button.MaterialButton
    private lateinit var btnSavePng: com.google.android.material.button.MaterialButton
    private lateinit var btnExportCsv: com.google.android.material.button.MaterialButton
    private lateinit var poseOverlay: PoseOverlayView
    private lateinit var btnOverlayVideo: com.google.android.material.button.MaterialButton
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var progressText: android.widget.TextView

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var pose: PoseLandmarker? = null

    // 片側・モード（必要ならUIで切り替え）
    private var side: Side = Side.LEFT
    private var mode: Mode = Mode.ABDUCTION

    private val smoother = EmaSmoother(0.2)
    private var lastInferenceMs: Long = 0
    private var currentAngle: Double? = null
    private var peakAngle: Double? = null
    private var lastFrameBitmap: Bitmap? = null
    private val samples = mutableListOf<Sample>()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else onCameraPermissionDenied() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.preview)
        angleText = findViewById(R.id.angleText)
        statusText = findViewById(R.id.statusText)
        sideGroup = findViewById(R.id.sideGroup)
        modeGroup = findViewById(R.id.modeGroup)
        btnResetPeak = findViewById(R.id.btnResetPeak)
        btnSavePng = findViewById(R.id.btnSavePng)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        btnOverlayVideo = findViewById(R.id.btnOverlayVideo)
        poseOverlay = findViewById(R.id.poseOverlay)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        statusText.text = "MODE: ${mode.name} ${if (side==Side.LEFT) "L" else "R"}"

        // MediaPipe Pose 初期化（assetsに pose_landmarker_lite.task を配置）
        try {
            initPose("pose_landmarker_lite.task")
        } catch (t: Throwable) {
            Toast.makeText(this, "Pose model init failed: ${t.message}", Toast.LENGTH_LONG).show()
        }

        // 初期選択
        sideGroup.check(R.id.btnSideLeft)
        modeGroup.check(R.id.btnModeAbd)

        // サイド切替
        sideGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            side = when (checkedId) {
                R.id.btnSideLeft -> Side.LEFT
                R.id.btnSideRight -> Side.RIGHT
                else -> side
            }
            updateStatus()
            resetSession()
        }

        // モード切替
        modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mode = when (checkedId) {
                R.id.btnModeAbd -> Mode.ABDUCTION
                R.id.btnModeFlex -> Mode.FLEXION
                R.id.btnModeExt -> Mode.EXTENSION
                else -> mode
            }
            updateStatus()
            resetSession()
        }

        btnResetPeak.setOnClickListener { resetSession() }
        btnSavePng.setOnClickListener { savePngWithOverlay() }
        btnExportCsv.setOnClickListener { exportCsv() }
        btnOverlayVideo.setOnClickListener { pickVideoForOverlay() }

        if (allPermissionsGranted()) startCamera()
        else requestPermission.launch(Manifest.permission.CAMERA)
    }

    private fun updateStatus() {
        statusText.text = "MODE: ${mode.name.take(3)} ${if (side==Side.LEFT) "L" else "R"}"
    }

    private fun resetSession() {
        peakAngle = null
        currentAngle = null
        samples.clear()
        Toast.makeText(this, "Session reset", Toast.LENGTH_SHORT).show()
    }

    private fun savePngWithOverlay() {
        val src = lastFrameBitmap ?: run {
            Toast.makeText(this, "No frame available", Toast.LENGTH_SHORT).show()
            return
        }
        val angle = currentAngle ?: peakAngle
        val display = angle?.let { String.format("%.1f°", it) } ?: "--.-°"
        val modeStr = mode.name.take(3)
        val sideStr = if (side == Side.LEFT) "L" else "R"

        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val paintBg = Paint().apply { color = Color.parseColor("#66000000") }
        val paintText = Paint().apply {
            color = Color.WHITE
            textSize = (bmp.width * 0.06f)
            isAntiAlias = true
        }
        val padding = (bmp.width * 0.02f)
        val text = "$display  [$modeStr $sideStr]"
        val tw = paintText.measureText(text)
        val th = paintText.fontMetrics.run { bottom - top }
        val left = (bmp.width - tw) / 2f - padding
        val top = padding * 2
        canvas.drawRect(left, top, left + tw + padding * 2, top + th + padding * 2, paintBg)
        canvas.drawText(text, left + padding, top + padding - paintText.fontMetrics.top, paintText)

        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
            if (!dir.exists()) dir.mkdirs()
            val name = "rom_${modeStr}_${sideStr}_${System.currentTimeMillis()}.png"
            val file = java.io.File(dir, name)
            java.io.FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(this, "Saved PNG: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save PNG failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportCsv() {
        if (samples.isEmpty()) {
            Toast.makeText(this, "No samples to export", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)!!
            if (!dir.exists()) dir.mkdirs()
            val modeStr = mode.name.take(3)
            val sideStr = if (side == Side.LEFT) "L" else "R"
            val name = "rom_${modeStr}_${sideStr}_${System.currentTimeMillis()}.csv"
            val file = java.io.File(dir, name)
            java.io.BufferedWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(file))).use { w ->
                w.write("timestamp,mode,side,angle\n")
                samples.forEach { s ->
                    w.write("${'$'}{s.timestamp},${'$'}{s.mode.name},${'$'}{s.side.name},${'$'}{String.format(\"%.3f\", s.angle)}\n")
                }
            }
            Toast.makeText(this, "Exported CSV: ${'$'}{file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export CSV failed: ${'$'}{e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private data class Sample(val timestamp: Long, val mode: Mode, val side: Side, val angle: Double)

    private fun initPose(modelAsset: String) {
        val base = BaseOptions.builder().setModelAssetPath(modelAsset).build()
        val options = PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setMinPoseDetectionConfidence(0.6f)
            .setMinPosePresenceConfidence(0.6f)
            .setMinTrackingConfidence(0.6f)
            .setNumPoses(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { r, _, _ -> onPoseResult(r) }
            .setErrorListener { e -> e.printStackTrace() }
            .build()
        pose = PoseLandmarker.createFromOptions(this, options)
    }

    private fun onPoseResult(r: PoseLandmarkerResult) {
        val angleRaw = AngleCalculator.compute(r, side, mode)
        // Update overlay with normalized landmarks (x,y in [0,1])
        val pts = if (r.landmarks().isNotEmpty()) r.landmarks()[0].map { android.graphics.PointF(it.x(), it.y()) } else emptyList()
        runOnUiThread {
            poseOverlay.setLandmarksNormalized(pts)
            val qualityOk = angleRaw != null
            if (angleRaw != null) {
                val a = smoother.push(angleRaw).coerceIn(0.0, 180.0)
                currentAngle = a
                peakAngle = if (peakAngle == null) a else kotlin.math.max(peakAngle!!, a)
                samples.add(Sample(System.currentTimeMillis(), mode, side, a))
                val peakDisp = peakAngle ?: a
                angleText.text = String.format("%.1f° (peak %.1f°)", a, peakDisp)
                poseOverlay.setOverlayText(String.format("%.1f°", a))
                val sideStr = if (side == Side.LEFT) "L" else "R"
                poseOverlay.setStatus(currentAngle, peakAngle, mode, qualityOk, sideStr)
            } else {
                angleText.text = "--.-°"
                poseOverlay.setOverlayText(null)
                val sideStr = if (side == Side.LEFT) "L" else "R"
                poseOverlay.setStatus(null, peakAngle, mode, qualityOk, sideStr)
                currentAngle = null
            }
        }
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun onCameraPermissionDenied() {
        val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        if (showRationale) {
            val dlg = android.app.AlertDialog.Builder(this)
                .setTitle("Camera permission required")
                .setMessage("Camera access is needed to measure angles.")
                .setPositiveButton("Grant") { d, _ ->
                    d.dismiss()
                    requestPermission.launch(Manifest.permission.CAMERA)
                }
                .setNegativeButton("Exit") { _, _ -> finish() }
                .create()
            dlg.show()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val now = SystemClock.elapsedRealtime()
                    // Throttle to ~20 FPS
                    if (now - lastInferenceMs >= 50) {
                        lastInferenceMs = now
                        val bmp = imageProxy.toBitmapRGBA8888Safe()
                        val rotated = bmp.rotate(imageProxy.imageInfo.rotationDegrees)
                        lastFrameBitmap = rotated
                        val mp: MPImage = BitmapImageBuilder(rotated).build()
                        pose?.detectAsync(mp, now)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        pose?.close()
        cameraExecutor.shutdown()
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showProgress(0)
            VideoOverlay.process(this, uri, mode, side, onProgress = { done, total ->
                val pct = if (total > 0) (done * 100 / total) else 0
                runOnUiThread { showProgress(pct) }
            }) { out ->
                runOnUiThread {
                    hideProgress()
                    if (out != null) {
                        // Launch in-app player
                        val intent = android.content.Intent(this, PlayerActivity::class.java)
                        // Pass as string Uri (file:// or content://)
                        intent.putExtra("uri", android.net.Uri.fromFile(java.io.File(out)).toString())
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Failed to overlay video", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showProgress(percent: Int) {
        progressBar.visibility = android.view.View.VISIBLE
        progressText.visibility = android.view.View.VISIBLE
        progressBar.progress = percent
        progressText.text = "Processing… ${percent}%"
    }

    private fun hideProgress() {
        progressBar.visibility = android.view.View.GONE
        progressText.visibility = android.view.View.GONE
    }

    private fun pickVideoForOverlay() {
        pickVideoLauncher.launch(arrayOf("video/*"))
    }
}

 

/** ===== ここから下は補助（1ファイル集約版） ===== */

private fun ImageProxy.toBitmapRGBA8888Safe(): Bitmap {
    // Expect RGBA_8888 (single plane)
    val plane = planes[0]
    val buffer: ByteBuffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride // should be 4
    val width = width
    val height = height

    // Some devices may have row padding (rowStride > width*4)
    return if (rowStride == width * 4 && pixelStride == 4) {
        buffer.rewind()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buffer)
        bmp
    } else {
        // Copy row by row into a tightly packed buffer
        val tight = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        val row = ByteArray(width * 4)
        buffer.rewind()
        for (y in 0 until height) {
            val rowStart = y * rowStride
            buffer.position(rowStart)
            buffer.get(row, 0, width * 4)
            tight.put(row)
        }
        tight.rewind()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(tight)
        bmp
    }
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val m = Matrix()
    m.postRotate(degrees.toFloat())
    return Bitmap.createBitmap(this, 0, 0, this.width, this.height, m, true)
}

// moved shared types/logic to model/Types.kt

private class EmaSmoother(private val alpha: Double = 0.08) {
    private var y: Double? = null
    fun push(x: Double): Double {
        y = if (y==null) x else (alpha*x + (1-alpha)*y!!)
        return y!!
    }
}
