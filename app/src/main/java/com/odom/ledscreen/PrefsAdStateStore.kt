package com.odom.ledscreen

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PrefsAdStateStore(context: Context) : AdStateStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ads_state", Context.MODE_PRIVATE)

    override var settingChangeCount: Int
        get() = prefs.getInt(KEY_SETTING_CHANGES, 0)
        set(value) = prefs.edit { putInt(KEY_SETTING_CHANGES, value) }

    override var lastInterstitialAtMs: Long
        get() = prefs.getLong(KEY_LAST_INTERSTITIAL, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_INTERSTITIAL, value) }

    override var resultUseCount: Int
        get() = prefs.getInt(KEY_RESULT_USES, 0)
        set(value) = prefs.edit { putInt(KEY_RESULT_USES, value) }

    override var reviewRequested: Boolean
        get() = prefs.getBoolean(KEY_REVIEW_REQUESTED, false)
        set(value) = prefs.edit { putBoolean(KEY_REVIEW_REQUESTED, value) }

    /** 앱 설치 후 최초 호출에서만 true. 호출 즉시 소모된다. */
    fun consumeFirstLaunch(): Boolean {
        val first = !prefs.getBoolean(KEY_HAS_LAUNCHED, false)
        if (first) prefs.edit { putBoolean(KEY_HAS_LAUNCHED, true) }
        return first
    }

    private companion object {
        const val KEY_SETTING_CHANGES = "setting_change_count"
        const val KEY_LAST_INTERSTITIAL = "last_interstitial_at_ms"
        const val KEY_RESULT_USES = "result_use_count"
        const val KEY_REVIEW_REQUESTED = "review_requested"
        const val KEY_HAS_LAUNCHED = "has_launched"
    }
}

/** 프로세스 단위 "최초 실행 세션" 판정. 화면 회전으로 Activity가 재생성돼도 값이 유지된다. */
object FirstSession {
    @Volatile
    private var cached: Boolean? = null

    fun isFirstSession(store: PrefsAdStateStore): Boolean =
        cached ?: store.consumeFirstLaunch().also { cached = it }
}
