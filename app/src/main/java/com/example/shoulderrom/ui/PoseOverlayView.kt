package com.example.shoulderrom.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.shoulderrom.model.Mode

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pointPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val linePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val lineBgPaint = Paint().apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    @Volatile
    private var normalizedPoints: List<PointF>? = null
    @Volatile
    private var overlayText: String? = null
    @Volatile
    private var qualityOk: Boolean = true
    @Volatile
    private var currentAngle: Double? = null
    @Volatile
    private var peakAngle: Double? = null
    @Volatile
    private var mode: Mode = Mode.ABDUCTION
    @Volatile
    private var sideLabel: String = "L"

    fun setLandmarksNormalized(points: List<PointF>?) {
        normalizedPoints = points
        postInvalidateOnAnimation()
    }

    fun setOverlayText(text: String?) {
        overlayText = text
        postInvalidateOnAnimation()
    }

    fun setStatus(current: Double?, peak: Double?, m: Mode, ok: Boolean, side: String) {
        currentAngle = current
        peakAngle = peak
        mode = m
        qualityOk = ok
        sideLabel = side
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = normalizedPoints ?: return
        if (pts.isEmpty()) return

        // Map normalized [0..1] to view pixels
        fun map(p: PointF): PointF = PointF(p.x * width, p.y * height)

        // Draw a subset: shoulders(11,12), elbows(13,14), wrists(15,16), hips(23,24)
        val idxs = listOf(11,12,13,14,15,16,23,24)
        val m = HashMap<Int, PointF>()
        idxs.forEach { i ->
            if (i < pts.size) m[i] = map(pts[i])
        }

        fun drawBone(a: Int, b: Int) {
            val pa = m[a]; val pb = m[b]
            if (pa != null && pb != null) {
                canvas.drawLine(pa.x, pa.y, pb.x, pb.y, lineBgPaint)
                canvas.drawLine(pa.x, pa.y, pb.x, pb.y, linePaint)
            }
        }

        // Upper body lines (monochrome)
        drawBone(11, 12) // shoulders
        drawBone(11, 13); drawBone(13, 15) // left arm
        drawBone(12, 14); drawBone(14, 16) // right arm
        drawBone(23, 24) // hips
        drawBone(11, 23); drawBone(12, 24) // torso

        // Joints points
        m.values.forEach { p ->
            // outer dark ring then inner white
            canvas.drawCircle(p.x, p.y, 8f, lineBgPaint)
            canvas.drawCircle(p.x, p.y, 5f, pointPaint)
        }

        // Draw measurement axes (移動軸・基本軸)
        val shIdx = if (sideLabel == "L") 11 else 12
        val elIdx = if (sideLabel == "L") 13 else 14
        val sh = m[shIdx]
        val el = m[elIdx]
        val hipL = m[23]
        val hipR = m[24]
        if (sh != null && el != null && hipL != null && hipR != null) {
            val midHip = PointF((hipL.x + hipR.x) / 2f, (hipL.y + hipR.y) / 2f)
            val axisPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 8f), 0f)
            }
            when (mode) {
                Mode.ABDUCTION -> {
                    // Moving axis: elbow → shoulder (yellow/orange)
                    axisPaint.color = Color.parseColor("#FFC800")
                    canvas.drawLine(el.x, el.y, sh.x, sh.y, axisPaint)
                    // Base axis: vertical line (body midline) through shoulder (cyan)
                    axisPaint.color = Color.parseColor("#00C8FF")
                    canvas.drawLine(sh.x, sh.y - 100f, sh.x, sh.y + 100f, axisPaint)
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
                    canvas.drawLine(sh.x, sh.y, sh.x, sh.y + 100f, axisPaint)
                }
            }
        }

        // Angle text
        overlayText?.let { t ->
            val pad = 16f
            val tw = textPaint.measureText(t)
            val fm = textPaint.fontMetrics
            val th = fm.bottom - fm.top
            val left = (width - tw) / 2f - pad
            val top = pad * 2
            val bg = Paint().apply { color = Color.parseColor("#99000000") }
            val rr = RectF(left, top, left + tw + pad * 2, top + th + pad * 2)
            canvas.drawRoundRect(rr, 12f, 12f, bg)
            canvas.drawText(t, left + pad, top + pad - fm.top, textPaint)

            // Secondary: mode/side below angle
            val subText = "${mode.name.take(3)} ${sideLabel}"
            val stw = textPaint.measureText(subText)
            val srr = RectF(
                (width - stw) / 2f - pad,
                top + th + pad * 3,
                (width + stw) / 2f + pad,
                top + th + pad * 3 + th + pad * 2
            )
            canvas.drawRoundRect(srr, 12f, 12f, bg)
            canvas.drawText(subText, (width - stw) / 2f, srr.top + pad - fm.top, textPaint)
        }

        // ROM bar at bottom
        val maxAngle = when (mode) {
            Mode.EXTENSION -> 50.0
            else -> 180.0
        }
        val barMargin = 24f
        val barHeight = 16f
        val left = barMargin
        val right = width - barMargin
        val bottom = height - barMargin
        val top = bottom - barHeight
        val barBg = Paint().apply { color = Color.parseColor("#66000000") }
        val barOk = Paint().apply { color = Color.parseColor("#88FFFFFF") } // neutral range fill stronger
        val barStroke = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
        // target window (rough references)
        val winStart = when (mode) {
            Mode.EXTENSION -> 40.0
            else -> 150.0
        }
        val winEnd = when (mode) {
            Mode.EXTENSION -> 50.0
            else -> 180.0
        }
        val xOf = { deg: Double -> left + ((deg / maxAngle).coerceIn(0.0,1.0) * (right - left)).toFloat() }

        // draw bar background and target window
        canvas.drawRect(left, top, right, bottom, barBg)
        canvas.drawRect(xOf(winStart), top, xOf(winEnd), bottom, barOk)
        canvas.drawRect(left, top, right, bottom, barStroke)

        // current and peak markers
        currentAngle?.let { a ->
            val x = xOf(a)
            canvas.drawLine(x, top - 8f, x, bottom + 8f, Paint().apply { color = Color.WHITE; strokeWidth = 3f })
        }
        peakAngle?.let { p ->
            val x = xOf(p)
            canvas.drawLine(x, top - 6f, x, bottom + 6f, Paint().apply { color = Color.parseColor("#88FFFFFF"); strokeWidth = 2f })
        }
    }
}
