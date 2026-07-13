package com.odom.ledscreen

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView

enum class MarqueeSpeed(val dpPerSecond: Float) {
    SLOW(80f), NORMAL(160f), FAST(320f);

    companion object {
        fun fromName(name: String?): MarqueeSpeed =
            values().firstOrNull { it.name == name } ?: NORMAL
    }
}

/**
 * ObjectAnimator 기반 무한 마퀴. textView가 container 가로 중앙에 배치된 상태를 전제로,
 * 화면 밖(한쪽) → 화면 밖(반대쪽)을 일정 px/s로 왕복 없이 반복(RESTART)한다.
 * 텍스트 길이와 무관하게 체감 속도가 일정하다.
 */
class MarqueeController {
    private var animator: ValueAnimator? = null

    fun start(textView: TextView, container: View, direction: String, speed: MarqueeSpeed) {
        stop(textView)
        container.post {
            val textWidth = textView.width.toFloat()
            if (textWidth <= 0f || container.width <= 0) return@post

            val travel = container.width + textWidth
            val half = travel / 2f
            val (from, to) = when (direction) {
                "LEFT" -> half to -half
                "RIGHT" -> -half to half
                else -> return@post
            }

            val pxPerSecond = speed.dpPerSecond * textView.resources.displayMetrics.density
            animator = ValueAnimator.ofFloat(from, to).apply {
                duration = durationMs(travel, pxPerSecond)
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { textView.translationX = it.animatedValue as Float }
                start()
            }
        }
    }

    fun stop(textView: TextView) {
        animator?.cancel()
        animator = null
        textView.translationX = 0f
    }

    companion object {
        fun durationMs(travelPx: Float, pxPerSecond: Float): Long {
            require(pxPerSecond > 0f) { "pxPerSecond must be > 0" }
            return (travelPx / pxPerSecond * 1000f).toLong().coerceAtLeast(1L)
        }
    }
}
