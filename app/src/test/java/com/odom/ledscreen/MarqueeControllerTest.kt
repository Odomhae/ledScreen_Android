package com.odom.ledscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class MarqueeControllerTest {

    @Test
    fun durationIsTravelOverSpeed() {
        // 1000px를 500px/s로 → 2초
        assertEquals(2000L, MarqueeController.durationMs(1000f, 500f))
    }

    @Test
    fun durationNeverZero() {
        assertEquals(1L, MarqueeController.durationMs(0f, 500f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun speedMustBePositive() {
        MarqueeController.durationMs(100f, 0f)
    }

    @Test
    fun speedFromNameFallsBackToNormal() {
        assertEquals(MarqueeSpeed.NORMAL, MarqueeSpeed.fromName(null))
        assertEquals(MarqueeSpeed.NORMAL, MarqueeSpeed.fromName("weird"))
        assertEquals(MarqueeSpeed.FAST, MarqueeSpeed.fromName("FAST"))
    }
}
