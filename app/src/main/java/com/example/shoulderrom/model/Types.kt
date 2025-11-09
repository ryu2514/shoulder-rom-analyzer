package com.example.shoulderrom.model

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.acos
import kotlin.math.sqrt

data class P3(val x: Float, val y: Float, val z: Float)

enum class Side { LEFT, RIGHT }
enum class Mode { ABDUCTION, FLEXION, EXTENSION }

object AngleCalculator {
    private fun idx(side: Side, joint: String): Int = when (joint) {
        "shoulder" -> if (side==Side.LEFT) 11 else 12
        "elbow"    -> if (side==Side.LEFT) 13 else 14
        "wrist"    -> if (side==Side.LEFT) 15 else 16
        else       -> if (side==Side.LEFT) 23 else 24
    }
    private fun lm(r: PoseLandmarkerResult, i: Int): P3 {
        val l = r.landmarks()[0][i]
        return P3(l.x(), l.y(), l.z())
    }
    private fun mid(a: P3, b: P3) = P3((a.x+b.x)/2f, (a.y+b.y)/2f, (a.z+b.z)/2f)

    private fun angleBetween(a: P3, b: P3, c: P3): Double {
        val ba = floatArrayOf(a.x-b.x, a.y-b.y, a.z-b.z)
        val bc = floatArrayOf(c.x-b.x, c.y-b.y, c.z-b.z)
        val dot = ba[0]*bc[0] + ba[1]*bc[1] + ba[2]*bc[2]
        val na = sqrt(ba[0]*ba[0] + ba[1]*ba[1] + ba[2]*ba[2])
        val nc = sqrt(bc[0]*bc[0] + bc[1]*bc[1] + bc[2]*bc[2])
        val cosv = (dot / (na*nc)).coerceIn(-1.0f, 1.0f)
        return Math.toDegrees(acos(cosv.toDouble()))
    }
    private fun angle2D(a: P3, b: P3, c: P3, plane: String = "xy"): Double {
        val pa = when (plane) { "xz" -> P3(a.x, 0f, a.z); "yz" -> P3(0f, a.y, a.z); else -> P3(a.x, a.y, 0f) }
        val pb = when (plane) { "xz" -> P3(b.x, 0f, b.z); "yz" -> P3(0f, b.y, b.z); else -> P3(b.x, b.y, 0f) }
        val pc = when (plane) { "xz" -> P3(c.x, 0f, c.z); "yz" -> P3(0f, c.y, c.z); else -> P3(c.x, c.y, 0f) }
        return angleBetween(pa, pb, pc)
    }

    fun compute(r: PoseLandmarkerResult, side: Side, mode: Mode): Double? {
        if (r.landmarks().isEmpty()) return null
        val sh = lm(r, idx(side,"shoulder"))
        val el = lm(r, idx(side,"elbow"))
        val wr = lm(r, idx(side,"wrist"))
        val hipL = lm(r, 23); val hipR = lm(r, 24)
        val shL  = lm(r, 11); val shR  = lm(r, 12)
        val midHip = mid(hipL, hipR)
        val shouldersZDiff = kotlin.math.abs(shL.z - shR.z)

        if (mode == Mode.ABDUCTION && shouldersZDiff > 0.12f) return null
        if ((mode == Mode.FLEXION || mode == Mode.EXTENSION) && shouldersZDiff < 0.10f) return null

        return when(mode){
            Mode.ABDUCTION -> {
                angle2D(el, sh, midHip, plane = "xy")
            }
            Mode.FLEXION -> {
                angle2D(el, sh, midHip, plane = "yz")
            }
            Mode.EXTENSION -> {
                // Extension: angle from vertical to shoulder-elbow vector
                // Arm backward (elbow behind shoulder in Z) = positive extension
                val dy = el.y - sh.y  // vertical: positive = elbow below shoulder
                val dz = sh.z - el.z  // depth: positive = elbow behind shoulder (MediaPipe Z: negative=close, positive=far)
                // When arm extends backward, dz should be positive
                if (dz < 0.01) return null  // arm not going backward
                val extAngle = Math.atan2(dz.toDouble(), kotlin.math.abs(dy.toDouble()) + 0.01) * 180.0 / Math.PI
                extAngle.coerceIn(0.0, 50.0)
            }
        }
    }

    // Test-friendly path using raw P3 landmarks array (size >= 25 as MediaPipe indices)
    fun computeFromP3(landmarks: List<P3>, side: Side, mode: Mode): Double? {
        if (landmarks.size < 25) return null
        fun lmI(i: Int) = landmarks[i]
        fun idxOf(j: String) = when (j) {
            "shoulder" -> if (side==Side.LEFT) 11 else 12
            "elbow" -> if (side==Side.LEFT) 13 else 14
            "wrist" -> if (side==Side.LEFT) 15 else 16
            else -> if (side==Side.LEFT) 23 else 24
        }
        val sh = lmI(idxOf("shoulder"))
        val el = lmI(idxOf("elbow"))
        val wr = lmI(idxOf("wrist"))
        val hipL = lmI(23); val hipR = lmI(24)
        val shL  = lmI(11); val shR  = lmI(12)
        val midHip = mid(hipL, hipR)
        val shouldersZDiff = kotlin.math.abs(shL.z - shR.z)

        if (mode == Mode.ABDUCTION && shouldersZDiff > 0.12f) return null
        if ((mode == Mode.FLEXION || mode == Mode.EXTENSION) && shouldersZDiff < 0.10f) return null

        return when(mode){
            Mode.ABDUCTION -> angle2D(el, sh, midHip, plane = "xy")
            Mode.FLEXION   -> angle2D(el, sh, midHip, plane = "yz")
            Mode.EXTENSION -> {
                val dy = el.y - sh.y
                val dz = sh.z - el.z
                if (dz < 0.01) return null
                val extAngle = Math.atan2(dz.toDouble(), kotlin.math.abs(dy.toDouble()) + 0.01) * 180.0 / Math.PI
                extAngle.coerceIn(0.0, 50.0)
            }
        }
    }
}
