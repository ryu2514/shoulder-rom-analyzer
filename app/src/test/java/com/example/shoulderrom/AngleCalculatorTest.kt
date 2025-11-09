package com.example.shoulderrom

import com.example.shoulderrom.model.AngleCalculator
import com.example.shoulderrom.model.Mode
import com.example.shoulderrom.model.P3
import com.example.shoulderrom.model.Side
import org.junit.Assert.*
import org.junit.Test

class AngleCalculatorTest {

    private fun blankLandmarks(): MutableList<P3> = MutableList(25) { P3(0f,0f,0f) }

    @Test
    fun `ABD front view passes, side view blocks`() {
        val lm = blankLandmarks()
        // hips
        lm[23] = P3(0.5f, 0.8f, 0f); lm[24] = P3(0.6f, 0.8f, 0f)
        // front view: small Z diff
        lm[11] = P3(0.5f, 0.5f, 0.0f)
        lm[12] = P3(0.6f, 0.5f, 0.01f)
        // arm relaxed
        lm[13] = P3(0.5f, 0.7f, 0f)
        val pass = AngleCalculator.computeFromP3(lm, Side.LEFT, Mode.ABDUCTION)
        assertNotNull(pass)

        // side view: large Z diff
        lm[11] = P3(0.5f, 0.5f, 0.0f)
        lm[12] = P3(0.6f, 0.5f, 0.3f)
        val block = AngleCalculator.computeFromP3(lm, Side.LEFT, Mode.ABDUCTION)
        assertNull(block)
    }
}

