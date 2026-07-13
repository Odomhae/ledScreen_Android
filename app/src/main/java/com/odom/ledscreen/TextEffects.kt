package com.odom.ledscreen

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.widget.TextView

object TextEffects {
    private val RAINBOW = intArrayOf(
        Color.parseColor("#FF1744"), Color.parseColor("#FF9100"),
        Color.parseColor("#FFEA00"), Color.parseColor("#00E676"),
        Color.parseColor("#00E5FF"), Color.parseColor("#2979FF"),
        Color.parseColor("#D500F9")
    )

    /** 텍스트 폭 기준 무지개 그라데이션. 텍스트가 바뀌면 다시 호출해야 한다. */
    fun applyRainbow(textView: TextView) {
        val width = textView.paint.measureText(textView.text.toString()).coerceAtLeast(1f)
        textView.paint.shader =
            LinearGradient(0f, 0f, width, 0f, RAINBOW, null, Shader.TileMode.MIRROR)
        textView.invalidate()
    }

    fun clear(textView: TextView) {
        textView.paint.shader = null
        textView.invalidate()
    }
}
