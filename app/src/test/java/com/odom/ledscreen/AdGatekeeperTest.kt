package com.odom.ledscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore : AdStateStore {
    override var settingChangeCount = 0
    override var lastInterstitialAtMs = 0L
    override var resultUseCount = 0
    override var reviewRequested = false
}

class AdGatekeeperTest {

    private fun gatekeeper(
        store: AdStateStore,
        firstSession: Boolean = false,
        now: Long = 10 * 60_000L
    ) = AdGatekeeper(store, firstSession) { now }

    @Test
    fun noInterstitialBelowThreshold() {
        val gk = gatekeeper(FakeStore())
        repeat(4) { gk.onSettingChanged() }
        assertFalse(gk.shouldShowInterstitialOnStart())
    }

    @Test
    fun interstitialAtThreshold() {
        val gk = gatekeeper(FakeStore())
        repeat(5) { gk.onSettingChanged() }
        assertTrue(gk.shouldShowInterstitialOnStart())
    }

    @Test
    fun noInterstitialOnFirstSession() {
        val gk = gatekeeper(FakeStore(), firstSession = true)
        repeat(10) { gk.onSettingChanged() }
        assertFalse(gk.shouldShowInterstitialOnStart())
    }

    @Test
    fun noInterstitialWithinTwoMinutesOfPrevious() {
        val store = FakeStore()
        store.lastInterstitialAtMs = 9 * 60_000L
        val gk = gatekeeper(store, now = 10 * 60_000L) // 1분 경과
        repeat(5) { gk.onSettingChanged() }
        assertFalse(gk.shouldShowInterstitialOnStart())
    }

    @Test
    fun interstitialAllowedAfterTwoMinutes() {
        val store = FakeStore()
        store.lastInterstitialAtMs = 7 * 60_000L
        val gk = gatekeeper(store, now = 10 * 60_000L) // 3분 경과
        repeat(5) { gk.onSettingChanged() }
        assertTrue(gk.shouldShowInterstitialOnStart())
    }

    @Test
    fun shownResetsCounterAndStampsTime() {
        val store = FakeStore()
        val gk = gatekeeper(store, now = 10 * 60_000L)
        repeat(5) { gk.onSettingChanged() }
        gk.onInterstitialShown()
        assertEquals(0, store.settingChangeCount)
        assertEquals(10 * 60_000L, store.lastInterstitialAtMs)
    }

    @Test
    fun reviewAfterThirdUseAndOnlyOnce() {
        val store = FakeStore()
        val gk = gatekeeper(store)
        repeat(2) { gk.onResultUsed() }
        assertFalse(gk.shouldRequestReview())
        gk.onResultUsed()
        assertTrue(gk.shouldRequestReview())
        gk.onReviewRequested()
        assertFalse(gk.shouldRequestReview())
    }

    @Test
    fun interstitialSkippedWhenReviewWillFireAfterNextUse() {
        val store = FakeStore()
        store.resultUseCount = 2 // 이번 사용이 3회째 → 복귀 시 리뷰 예정
        val gk = gatekeeper(store)
        repeat(5) { gk.onSettingChanged() }
        assertFalse(gk.shouldShowInterstitialOnStart())
    }
}
