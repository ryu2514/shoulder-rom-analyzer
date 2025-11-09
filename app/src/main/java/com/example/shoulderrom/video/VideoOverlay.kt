package com.example.shoulderrom.video

import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Environment
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.example.shoulderrom.model.AngleCalculator
import com.example.shoulderrom.model.Mode
import com.example.shoulderrom.model.Side
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.max

object VideoOverlay {

    private const val TARGET_FPS = 20
    private const val BITRATE = 4_000_000

    fun process(
        context: Context,
        uri: Uri,
        mode: Mode,
        side: Side,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        onDone: (String?) -> Unit
    ) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                var width = w; var height = h
                if (rotation == 90 || rotation == 270) {
                    width = h; height = w
                }
                // enforce even dimensions for encoder
                width = if (width % 2 == 0) width else width - 1
                height = if (height % 2 == 0) height else height - 1

                val outDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
                if (!outDir.exists()) outDir.mkdirs()
                val outFile = File(outDir, "overlay_${System.currentTimeMillis()}.mp4")

                val encoder = MediaCodec.createEncoderByType("video/avc")
                val format = MediaFormat.createVideoFormat("video/avc", width, height)
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                format.setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FPS)
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                encoder.start()

                // MediaPipe pose for IMAGE mode
                val pose = PoseLandmarker.createFromOptions(
                    context,
                    PoseLandmarkerOptions.builder()
                        .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
                        .setRunningMode(RunningMode.IMAGE)
                        .setNumPoses(1)
                        .build()
                )

                val paintLine = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
                val paintLineBg = Paint().apply { color = Color.parseColor("#80000000"); style = Paint.Style.STROKE; strokeWidth = 10f; isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
                val paintPoint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }

                val frameCount = max(1, ((durationMs / 1000.0) * TARGET_FPS).toInt())
                var trackIndex = -1
                var muxerStarted = false

                fun encodeBitmap(bmp: Bitmap, ptsUs: Long) {
                    // Scale/letterbox to exact encoder size
                    val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val c = Canvas(frame)
                    val src = Rect(0, 0, bmp.width, bmp.height)
                    val dst = Rect(0, 0, width, height)
                    c.drawBitmap(bmp, src, dst, null)

                    val inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val ib = encoder.getInputBuffer(inputIndex)!!
                        ib.clear()
                        // Convert to NV12
                        val yuv = argbToNV12(frame)
                        ib.put(yuv)
                        encoder.queueInputBuffer(inputIndex, 0, yuv.size, ptsUs, 0)
                    }

                    val bufferInfo = MediaCodec.BufferInfo()
                    while (true) {
                        val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                        if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (muxerStarted) throw RuntimeException("format changed twice")
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        } else if (outIndex >= 0) {
                            val ob = encoder.getOutputBuffer(outIndex) ?: continue
                            if (bufferInfo.size > 0 && muxerStarted) {
                                ob.position(bufferInfo.offset)
                                ob.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, ob, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outIndex, false)
                        }
                    }
                }

                var tUs = 0L
                val stepUs = 1_000_000L / TARGET_FPS
                var peakAngle: Double? = null
                for (i in 0 until frameCount) {
                    val frameBitmap = retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: break
                    // Rotate if needed
                    val rotated = if (rotation != 0) rotateBitmap(frameBitmap, rotation) else frameBitmap

                    // Pose → overlay draw on the bitmap
                    val mp: MPImage = BitmapImageBuilder(rotated).build()
                    val result = pose.detect(mp)
                    val canvas = Canvas(rotated)
                    val lm = if (result.landmarks().isNotEmpty()) result.landmarks()[0] else emptyList()
                    fun P(i: Int): PointF? = if (i in lm.indices) PointF(lm[i].x() * rotated.width, lm[i].y() * rotated.height) else null
                    fun L(a: Int, b: Int) {
                        val pa = P(a); val pb = P(b)
                        if (pa!=null && pb!=null) {
                            canvas.drawLine(pa.x, pa.y, pb.x, pb.y, paintLineBg)
                            canvas.drawLine(pa.x, pa.y, pb.x, pb.y, paintLine)
                        }
                    }
                    // Angle overlay
                    val angle = if (!result.landmarks().isEmpty()) AngleCalculator.compute(result, side, mode) else null

                    // skeleton subset
                    L(11,12); L(11,13); L(13,15); L(12,14); L(14,16); L(23,24); L(11,23); L(12,24)
                    listOf(11,12,13,14,15,16,23,24).forEach { idx -> P(idx)?.let { p ->
                        canvas.drawCircle(p.x, p.y, 8f, paintLineBg)
                        canvas.drawCircle(p.x, p.y, 5f, paintPoint)
                    } }

                    // Draw measurement axes (移動軸・基本軸)
                    val shIdx = if (side == Side.LEFT) 11 else 12
                    val elIdx = if (side == Side.LEFT) 13 else 14
                    val sh = P(shIdx)
                    val el = P(elIdx)
                    val hipL = P(23)
                    val hipR = P(24)
                    if (sh != null && el != null && hipL != null && hipR != null) {
                        val midHip = PointF((hipL.x + hipR.x) / 2f, (hipL.y + hipR.y) / 2f)
                        val axisPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 5f
                            pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                            isAntiAlias = true
                        }
                        when (mode) {
                            Mode.ABDUCTION -> {
                                // Moving axis: elbow → shoulder (yellow/orange)
                                axisPaint.color = Color.parseColor("#FFC800")
                                canvas.drawLine(el.x, el.y, sh.x, sh.y, axisPaint)
                                // Base axis: vertical line (body midline) through shoulder (cyan)
                                axisPaint.color = Color.parseColor("#00C8FF")
                                canvas.drawLine(sh.x, sh.y - 150f, sh.x, sh.y + 150f, axisPaint)
                            }
                            Mode.FLEXION -> {
                                // Moving axis: elbow → shoulder (yellow/orange)
                                axisPaint.color = Color.parseColor("#FFC800")
                                canvas.drawLine(el.x, el.y, sh.x, sh.y, axisPaint)
                                // Base axis: shoulder → hip midpoint (cyan)
                                axisPaint.color = Color.parseColor("#00C8FF")
                                canvas.drawLine(sh.x, sh.y, midHip.x, midHip.y, axisPaint)
                            }
                            Mode.EXTENSION -> {
                                // Moving axis: shoulder → elbow (yellow/orange)
                                axisPaint.color = Color.parseColor("#FFC800")
                                canvas.drawLine(sh.x, sh.y, el.x, el.y, axisPaint)
                                // Base axis: vertical downward from shoulder (cyan)
                                axisPaint.color = Color.parseColor("#00C8FF")
                                canvas.drawLine(sh.x, sh.y, sh.x, sh.y + 150f, axisPaint)
                            }
                        }
                    }

                    angle?.let { a ->
                        peakAngle = if (peakAngle == null) a else kotlin.math.max(peakAngle!!, a)
                        val text = String.format("%.1f°", a)
                        val paintText = Paint().apply { color = Color.WHITE; textSize = (rotated.width * 0.06f); isAntiAlias = true }
                        val paintBg = Paint().apply { color = Color.parseColor("#99000000") }
                        val pad = (rotated.width * 0.02f)
                        val tw = paintText.measureText(text)
                        val fm = paintText.fontMetrics
                        val th = fm.bottom - fm.top
                        val left = (rotated.width - tw) / 2f - pad
                        val top = pad * 2
                        val rr = android.graphics.RectF(left, top, left + tw + pad * 2, top + th + pad * 2)
                        canvas.drawRoundRect(rr, 12f, 12f, paintBg)
                        canvas.drawText(text, left + pad, top + pad - fm.top, paintText)

                        // Secondary label [MODE SIDE]
                        val sub = "${mode.name.take(3)} ${if (side==Side.LEFT) "L" else "R"}"
                        val stw = paintText.measureText(sub)
                        val r2 = android.graphics.RectF(
                            (rotated.width - stw) / 2f - pad,
                            top + th + pad * 3,
                            (rotated.width + stw) / 2f + pad,
                            top + th + pad * 3 + th + pad * 2
                        )
                        canvas.drawRoundRect(r2, 12f, 12f, paintBg)
                        canvas.drawText(sub, (rotated.width - stw) / 2f, r2.top + pad - fm.top, paintText)
                    }

                    // ROM bar (bottom)
                    val maxAngle = when (mode) {
                        Mode.EXTENSION -> 50.0
                        else -> 180.0
                    }
                    val barMargin = (rotated.width * 0.04f)
                    val barHeight = (rotated.height * 0.02f)
                    val l = barMargin
                    val r = rotated.width - barMargin
                    val b = rotated.height - barMargin
                    val t = b - barHeight
                    val barBg = Paint().apply { color = Color.parseColor("#66000000") }
                    val barOk = Paint().apply { color = Color.parseColor("#88FFFFFF") }
                    val barStroke = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
                    val winStart = when (mode) {
                        Mode.EXTENSION -> 40.0
                        else -> 150.0
                    }
                    val winEnd = when (mode) {
                        Mode.EXTENSION -> 50.0
                        else -> 180.0
                    }
                    fun xOf(deg: Double): Float = l + ((deg / maxAngle).coerceIn(0.0,1.0) * (r - l))
                    canvas.drawRect(l, t, r, b, barBg)
                    canvas.drawRect(xOf(winStart), t, xOf(winEnd), b, barOk)
                    canvas.drawRect(l, t, r, b, barStroke)
                    angle?.let { a -> canvas.drawLine(xOf(a), t - 8f, xOf(a), b + 8f, Paint().apply { color = Color.WHITE; strokeWidth = 3f }) }
                    peakAngle?.let { p -> canvas.drawLine(xOf(p), t - 6f, xOf(p), b + 6f, Paint().apply { color = Color.parseColor("#88FFFFFF"); strokeWidth = 2f }) }

                    encodeBitmap(rotated, tUs)
                    tUs += stepUs
                    onProgress?.invoke(i + 1, frameCount)
                }

                // drain encoder
                val inputIndex = encoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    encoder.queueInputBuffer(inputIndex, 0, 0, tUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                val bufferInfo = MediaCodec.BufferInfo()
                while (true) {
                    val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start(); muxerStarted = true
                        }
                    } else if (outIndex >= 0) {
                        val ob = encoder.getOutputBuffer(outIndex)
                        if (ob != null && bufferInfo.size > 0 && muxerStarted) {
                            ob.position(bufferInfo.offset)
                            ob.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, ob, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }

                encoder.stop(); encoder.release()
                muxer.stop(); muxer.release()
                retriever.release()
                pose.close()

                onDone(outFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                onDone(null)
            }
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        val m = Matrix(); m.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    // ARGB8888 -> NV12 (YUV420SP: Y plane + interleaved UV)
    private fun argbToNV12(bmp: Bitmap): ByteArray {
        val width = bmp.width
        val height = bmp.height
        val argb = IntArray(width * height)
        bmp.getPixels(argb, 0, width, 0, 0, width, height)

        val ySize = width * height
        val uvSize = ySize / 2
        val out = ByteArray(ySize + uvSize)
        var yIndex = 0
        var uvIndex = ySize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff

                var y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                var u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                var v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                y = y.coerceIn(0, 255)
                u = u.coerceIn(0, 255)
                v = v.coerceIn(0, 255)

                out[yIndex++] = y.toByte()

                if ((j % 2 == 0) && (i % 2 == 0)) {
                    out[uvIndex++] = u.toByte() // NV12: UV
                    out[uvIndex++] = v.toByte()
                }
            }
        }
        return out
    }
}
