package com.odom.ledscreen

/** 광고/리뷰 상태 저장소. 프로덕션은 SharedPreferences, 테스트는 인메모리 구현을 쓴다. */
interface AdStateStore {
    var settingChangeCount: Int
    var lastInterstitialAtMs: Long
    var resultUseCount: Int
    var reviewRequested: Boolean
}

/**
 * 전면광고/인앱리뷰 노출 판단 로직 (순수 Kotlin, Android 의존성 없음).
 * 규칙: 설정 변경 5회 누적 → START 진입 시 전면광고.
 * 단, 최초 실행 세션 / 직전 광고 후 2분 미경과 / 리뷰 예정 진입이면 스킵.
 */
class AdGatekeeper(
    private val store: AdStateStore,
    private val isFirstSession: Boolean,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    fun onSettingChanged() {
        store.settingChangeCount += 1
    }

    fun shouldShowInterstitialOnStart(): Boolean {
        if (isFirstSession) return false
        if (store.settingChangeCount < SETTING_CHANGE_THRESHOLD) return false
        if (nowMs() - store.lastInterstitialAtMs < MIN_INTERVAL_MS) return false
        if (willRequestReviewAfterNextUse()) return false
        return true
    }

    fun onInterstitialShown() {
        store.settingChangeCount = 0
        store.lastInterstitialAtMs = nowMs()
    }

    fun onResultUsed() {
        store.resultUseCount += 1
    }

    fun shouldRequestReview(): Boolean =
        !store.reviewRequested && store.resultUseCount >= REVIEW_USE_THRESHOLD

    fun onReviewRequested() {
        store.reviewRequested = true
    }

    private fun willRequestReviewAfterNextUse(): Boolean =
        !store.reviewRequested && store.resultUseCount + 1 >= REVIEW_USE_THRESHOLD

    companion object {
        const val SETTING_CHANGE_THRESHOLD = 5
        const val MIN_INTERVAL_MS = 2 * 60 * 1000L
        const val REVIEW_USE_THRESHOLD = 3
    }
}
