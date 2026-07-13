# LED Screen v1.3 — UI 폴리싱 + 수익화 개선 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 전면광고·종료 다이얼로그 광고·적응형 배너·인앱리뷰를 추가하고, 다크 LED 테마 폴리싱 + 속도 조절 + 전광판 화면 기본기 + 프리셋/무지개 효과를 v1.3 단일 릴리스로 배포한다.

**Architecture:** 광고 노출 판단 로직은 순수 Kotlin 클래스 `AdGatekeeper`(단위 테스트 대상)로 분리하고, AdMob SDK 호출은 `AdsManager`로 격리한다. 마퀴는 XML 애니메이션을 버리고 `MarqueeController`(ObjectAnimator 기반, duration 계산은 단위 테스트 대상)로 교체해 속도 조절과 끊김 없는 반복을 해결한다. Activity는 이 헬퍼들을 배선만 한다.

**Tech Stack:** Kotlin 1.8.20, AGP 8.3.2, Gradle 8.9, ViewBinding, Google Mobile Ads SDK 24.x (UMP 포함), Play In-App Review (review-ktx), JUnit4.

**Spec:** `docs/superpowers/specs/2026-07-12-ui-monetization-improvements-design.md`

## Global Constraints

- `minSdk 24`, `compileSdk 35`, `targetSdk 35`, Java 17, Kotlin **1.8.20** (→ enum `entries` 사용 금지, `values()` 사용)
- 릴리스 버전: **versionCode 5 / versionName "1.3"**
- 광고는 어떤 경우에도 앱 흐름을 막지 않는다: 미로드/실패 시 조용히 통과 (스피너·대기 금지)
- 전면광고 규칙: 설정 변경 5회 누적 → 다음 START 진입 시 표시. 최초 실행 세션 제외, 직전 광고 후 2분 미경과 시 제외, 인앱리뷰 예정 진입 제외
- 인앱리뷰: 전광판 3회 사용 후 메인 복귀 시 1회만 시도
- 개발 중에는 AdMob **테스트 광고 ID** 사용, 릴리스 직전 실제 ID로 교체 (Task 12)
- 빌드/테스트 명령 (Windows PowerShell): `.\gradlew.bat assembleDebug`, `.\gradlew.bat :app:testDebugUnitTest`
- 커밋은 태스크마다 1회 이상. 기존 미커밋 변경(`.idea/`, `app/release/` 등)은 절대 커밋에 포함하지 않는다 — 항상 파일을 명시해서 `git add`

## 파일 구조 (전체 조감)

| 파일 | 역할 |
|---|---|
| `app/src/main/java/com/odom/ledscreen/AdGatekeeper.kt` | **신규.** 광고/리뷰 노출 판단 순수 로직 + `AdStateStore` 인터페이스 |
| `app/src/main/java/com/odom/ledscreen/PrefsAdStateStore.kt` | **신규.** SharedPreferences 구현 + 최초실행 세션 판정 |
| `app/src/main/java/com/odom/ledscreen/AdsManager.kt` | **신규.** UMP 동의, 적응형 배너, 전면광고, 종료용 MREC |
| `app/src/main/java/com/odom/ledscreen/MarqueeController.kt` | **신규.** ObjectAnimator 무한 마퀴 + 속도(`MarqueeSpeed`) |
| `app/src/main/java/com/odom/ledscreen/TextEffects.kt` | **신규.** 무지개 글자 셰이더 |
| `app/src/test/java/com/odom/ledscreen/AdGatekeeperTest.kt` | **신규.** 단위 테스트 |
| `app/src/test/java/com/odom/ledscreen/MarqueeControllerTest.kt` | **신규.** 단위 테스트 |
| `app/src/main/res/layout/dialog_exit.xml` | **신규.** 종료 다이얼로그 |
| `MainActivity.kt`, `ResultActivity.kt` | 수정 (배선) |
| `activity_main.xml`, `activity_result.xml` | 수정 |
| `colors.xml`, `strings.xml`, `themes.xml`, `drawable/selector_background.xml` | 수정 |
| `app/build.gradle` | 수정 (의존성, 버전) |

---

### Task 1: 의존성 및 버전 업데이트

**Files:**
- Modify: `app/build.gradle`

**Interfaces:**
- Produces: GMA SDK 24.x (UMP 클래스 `com.google.android.ump.*` 포함), `com.google.android.play.core.review.ReviewManagerFactory` 사용 가능

- [ ] **Step 1: build.gradle 수정**

`app/build.gradle`의 `defaultConfig`에서:

```gradle
        versionCode 5
        versionName "1.3"
```

`dependencies` 블록에서 광고 의존성을 교체하고 리뷰 라이브러리를 추가:

```gradle
    implementation 'com.google.android.gms:play-services-ads:24.6.0'
    implementation 'com.google.android.play:review-ktx:2.0.2'
```

(기존 `play-services-ads:22.2.0` 줄은 삭제. `24.6.0` 해석 실패 시 24.x 최신 안정 버전으로 조정)

- [ ] **Step 2: 빌드 확인**

Run: `.\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (compileSdk 35 + AGP 8.3.2 경고는 무시 가능)

- [ ] **Step 3: Commit**

```powershell
git add app/build.gradle
git commit -m "build: GMA SDK 24.x + review-ktx, version 1.3 (5)"
```

---

### Task 2: AdGatekeeper — 광고/리뷰 판단 로직 (TDD)

**Files:**
- Create: `app/src/main/java/com/odom/ledscreen/AdGatekeeper.kt`
- Create: `app/src/main/java/com/odom/ledscreen/PrefsAdStateStore.kt`
- Test: `app/src/test/java/com/odom/ledscreen/AdGatekeeperTest.kt`

**Interfaces:**
- Produces:
  - `interface AdStateStore { var settingChangeCount: Int; var lastInterstitialAtMs: Long; var resultUseCount: Int; var reviewRequested: Boolean }`
  - `class AdGatekeeper(store: AdStateStore, isFirstSession: Boolean, nowMs: () -> Long = System::currentTimeMillis)` — 메서드: `onSettingChanged()`, `shouldShowInterstitialOnStart(): Boolean`, `onInterstitialShown()`, `onResultUsed()`, `shouldRequestReview(): Boolean`, `onReviewRequested()`
  - `class PrefsAdStateStore(context: Context) : AdStateStore` + `object FirstSession { fun isFirstSession(store: PrefsAdStateStore): Boolean }`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/odom/ledscreen/AdGatekeeperTest.kt`:

```kotlin
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
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.odom.ledscreen.AdGatekeeperTest"`
Expected: FAIL — `Unresolved reference: AdStateStore` (컴파일 에러 = TDD의 실패 단계)

- [ ] **Step 3: AdGatekeeper 구현**

`app/src/main/java/com/odom/ledscreen/AdGatekeeper.kt`:

```kotlin
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.odom.ledscreen.AdGatekeeperTest"`
Expected: PASS (8 tests)

- [ ] **Step 5: PrefsAdStateStore 구현**

`app/src/main/java/com/odom/ledscreen/PrefsAdStateStore.kt`:

```kotlin
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
```

- [ ] **Step 6: 전체 빌드 + 테스트**

Run: `.\gradlew.bat :app:testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 테스트 PASS

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/odom/ledscreen/AdGatekeeper.kt app/src/main/java/com/odom/ledscreen/PrefsAdStateStore.kt app/src/test/java/com/odom/ledscreen/AdGatekeeperTest.kt
git commit -m "feat: AdGatekeeper ad/review gating logic with unit tests"
```

---

### Task 3: AdsManager — UMP 동의 + 광고 SDK 래퍼

**Files:**
- Create: `app/src/main/java/com/odom/ledscreen/AdsManager.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `class AdsManager(activity: Activity)` — `start(onAdsAvailable: () -> Unit)`, `attachAdaptiveBanner(container: ViewGroup)`, `loadInterstitial()`, `val isInterstitialReady: Boolean`, `showInterstitial(onShown: () -> Unit, onDismissed: () -> Unit)`, `preloadExitAd()`, `val exitAdView: AdView?`, `val isExitAdLoaded: Boolean`, `destroy()`
- 문자열 리소스: `TEST_interstitial_ad_unit_id`, `TEST_banner_ad_unit_id` (개발용 = 테스트 ID)

- [ ] **Step 1: strings.xml에 광고 유닛 ID 추가**

`app/src/main/res/values/strings.xml`의 `</resources>` 직전에 추가 (Google 공식 테스트 유닛 — Task 12에서 실제 유닛으로 교체):

```xml
    <!-- 개발용 테스트 유닛. 릴리스 전 AdMob 콘솔에서 만든 실제 유닛으로 교체할 것 (Task 12) -->
    <string name="TEST_interstitial_ad_unit_id" translatable="false">ca-app-pub-3940256099942544/1033173712</string>
    <string name="TEST_banner_ad_unit_id" translatable="false">ca-app-pub-3940256099942544/6300978111</string>
```

- [ ] **Step 2: AdsManager 구현**

`app/src/main/java/com/odom/ledscreen/AdsManager.kt`:

```kotlin
package com.odom.ledscreen

import android.app.Activity
import android.util.DisplayMetrics
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/** AdMob SDK 호출을 한 곳에 모은 래퍼. 실패는 모두 조용히 삼키고 앱 흐름을 막지 않는다. */
class AdsManager(private val activity: Activity) {

    private val isMobileAdsInitialized = AtomicBoolean(false)
    private var interstitialAd: InterstitialAd? = null

    var exitAdView: AdView? = null
        private set
    var isExitAdLoaded = false
        private set

    val isInterstitialReady: Boolean get() = interstitialAd != null

    /** UMP 동의 확인 후 SDK 초기화. onAdsAvailable은 백그라운드 스레드에서 호출될 수 있다. */
    fun start(onAdsAvailable: () -> Unit) {
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInfo.requestConsentInfoUpdate(activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consentInfo.canRequestAds()) initMobileAds(onAdsAvailable)
                }
            },
            {
                // 동의 정보 갱신 실패 — 이전 세션의 동의가 유효하면 그대로 진행
                if (consentInfo.canRequestAds()) initMobileAds(onAdsAvailable)
            })

        // 이전 세션에서 이미 동의를 얻었으면 폼을 기다리지 않고 바로 초기화
        if (consentInfo.canRequestAds()) initMobileAds(onAdsAvailable)
    }

    private fun initMobileAds(onAdsAvailable: () -> Unit) {
        if (!isMobileAdsInitialized.compareAndSet(false, true)) return
        MobileAds.initialize(activity) {}
        onAdsAvailable()
    }

    // ---- 적응형 앵커 배너 ----

    fun attachAdaptiveBanner(container: ViewGroup) {
        val adView = AdView(activity)
        adView.adUnitId = activity.getString(R.string.REAL_banner_ad_unit_id)
        adView.setAdSize(adaptiveAdSize())
        container.removeAllViews()
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun adaptiveAdSize(): AdSize {
        val metrics: DisplayMetrics = activity.resources.displayMetrics
        val adWidth = (metrics.widthPixels / metrics.density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    // ---- 전면광고 ----

    fun loadInterstitial() {
        if (interstitialAd != null) return
        InterstitialAd.load(
            activity,
            activity.getString(R.string.TEST_interstitial_ad_unit_id),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            })
    }

    /**
     * 준비된 전면광고 표시. 광고가 없으면 onDismissed를 즉시 호출한다(블로킹 금지 원칙).
     * onShown은 실제 표시 시점, onDismissed는 닫힘/표시실패 시점에 호출.
     */
    fun showInterstitial(onShown: () -> Unit, onDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            onDismissed()
            return
        }
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                onShown()
            }

            override fun onAdDismissedFullScreenContent() {
                loadInterstitial() // 다음 광고 미리 로드
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadInterstitial()
                onDismissed()
            }
        }
        ad.show(activity)
    }

    // ---- 종료 다이얼로그용 MREC(300x250) ----

    fun preloadExitAd() {
        if (exitAdView != null) return
        val adView = AdView(activity)
        adView.adUnitId = activity.getString(R.string.TEST_banner_ad_unit_id)
        adView.setAdSize(AdSize.MEDIUM_RECTANGLE)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                isExitAdLoaded = true
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                isExitAdLoaded = false
            }
        }
        exitAdView = adView
        adView.loadAd(AdRequest.Builder().build())
    }

    fun destroy() {
        exitAdView?.destroy()
        exitAdView = null
        isExitAdLoaded = false
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/odom/ledscreen/AdsManager.kt app/src/main/res/values/strings.xml
git commit -m "feat: AdsManager with UMP consent, interstitial, exit MREC"
```

---

### Task 4: 적응형 배너 교체 + MainActivity 광고 초기화 배선

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml` (AdView → FrameLayout 컨테이너)
- Modify: `app/src/main/java/com/odom/ledscreen/MainActivity.kt`

**Interfaces:**
- Consumes: `AdsManager.start/attachAdaptiveBanner/loadInterstitial/preloadExitAd/destroy`, `AdGatekeeper`, `PrefsAdStateStore`, `FirstSession`
- Produces: `MainActivity`의 필드 `adsManager: AdsManager`, `gatekeeper: AdGatekeeper` (이후 태스크가 사용), 레이아웃 id `adContainer`

- [ ] **Step 1: activity_main.xml의 AdView를 컨테이너로 교체**

`activity_main.xml`에서 `<com.google.android.gms.ads.AdView ...>` 블록 전체(191~198행)를 다음으로 교체:

```xml
    <FrameLayout
        android:id="@+id/adContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:layout_constraintBottom_toBottomOf="parent" />
```

같은 파일의 ScrollView 제약도 수정: `app:layout_constraintBottom_toTopOf="@+id/adMobView"` → `app:layout_constraintBottom_toTopOf="@+id/adContainer"`

- [ ] **Step 2: MainActivity 광고 배선**

`MainActivity.kt`에서:

(a) 기존 광고 관련 멤버 삭제 — `lateinit var mAdView : AdView`와 `private val adSize: AdSize get() = ...` 블록(48~59행), 그리고 `onStart()` 오버라이드 전체(214~221행). 사용하지 않게 된 import(`AdRequest`, `AdSize`, `AdView`, `MobileAds`, `DisplayMetrics`) 삭제.

(b) 새 필드 추가 (`private lateinit var binding` 근처):

```kotlin
    private lateinit var adsManager: AdsManager
    private lateinit var gatekeeper: AdGatekeeper
```

(c) `onCreate`의 `setContentView(view)` 다음에 추가:

```kotlin
        val store = PrefsAdStateStore(this)
        gatekeeper = AdGatekeeper(store, FirstSession.isFirstSession(store))

        adsManager = AdsManager(this)
        adsManager.start {
            runOnUiThread {
                adsManager.attachAdaptiveBanner(binding.adContainer)
                adsManager.loadInterstitial()
                adsManager.preloadExitAd()
            }
        }
```

(d) `onDestroy` 오버라이드 추가 (클래스 끝부분):

```kotlin
    override fun onDestroy() {
        adsManager.destroy()
        super.onDestroy()
    }
```

- [ ] **Step 3: 빌드 + 수동 확인**

Run: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
에뮬레이터/기기에서 실행: 하단 배너가 화면 폭에 꽉 차게 표시되는지 확인 (REAL 배너 유닛이므로 광고 미표시 가능 — 로드 시도만 확인. 확실한 확인이 필요하면 임시로 `TEST_banner_ad_unit_id`를 참조해 보고 되돌린다).

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/res/layout/activity_main.xml app/src/main/java/com/odom/ledscreen/MainActivity.kt
git commit -m "feat: adaptive anchored banner via AdsManager"
```

---

### Task 5: 전면광고 배선 — 설정 변경 카운트 + START 게이트

**Files:**
- Modify: `app/src/main/java/com/odom/ledscreen/MainActivity.kt`

**Interfaces:**
- Consumes: `gatekeeper`, `adsManager` (Task 4에서 생성된 필드)
- Produces: `private fun launchResult()` — 이후 태스크(8, 11)가 Intent extra를 여기에 추가

- [ ] **Step 1: 설정 변경 카운트 추가**

`MainActivity.kt`에서 아래 4곳에 `gatekeeper.onSettingChanged()` 호출 추가:

(a) `onColorClick` 메서드 맨 위:

```kotlin
    override fun onColorClick(tagDialog: String, selectedColor: Int?) {
        gatekeeper.onSettingChanged()
        // ...기존 코드 그대로...
```

(b) `buttonBlink` 클릭 리스너 안 (if/else 앞에):

```kotlin
        buttonBlink.setOnClickListener {
            gatekeeper.onSettingChanged()
            val animBlink: Animation = AnimationUtils.loadAnimation(this, R.anim.blink)
            // ...기존 코드...
```

(c) `buttonLeft` 클릭 리스너 안 맨 위, (d) `buttonRight` 클릭 리스너 안 맨 위에 동일하게 추가.

(폰트 크기 +/−, 텍스트 입력은 카운트하지 않는다 — 스펙)

- [ ] **Step 2: START 버튼 게이트 적용**

기존 `buttonStart.setOnClickListener { ... }` 블록을 다음으로 교체:

```kotlin
        buttonStart.setOnClickListener {
            if (gatekeeper.shouldShowInterstitialOnStart() && adsManager.isInterstitialReady) {
                adsManager.showInterstitial(
                    onShown = { gatekeeper.onInterstitialShown() },
                    onDismissed = { launchResult() })
            } else {
                launchResult()
            }
        }
```

그리고 클래스에 메서드 추가 (기존 Intent 구성 코드를 그대로 이동):

```kotlin
    private fun launchResult() {
        val ledIntent = Intent(this, ResultActivity::class.java)
        ledIntent.putExtra("TextInput", textViewNote.text.toString())
        ledIntent.putExtra("BackColor", colorSelectorDialog1.selectedColor)
        ledIntent.putExtra("TextColor", colorSelectorDialog2.selectedColor)
        ledIntent.putExtra("fontSize", Fontsize)
        ledIntent.putExtra("Direction", TextDirection) // STOP / LEFT / RIGHT
        ledIntent.putExtra("isBlink", buttonBlink.isSelected)

        gatekeeper.onResultUsed()
        startActivity(ledIntent)
    }
```

- [ ] **Step 3: 빌드 + 수동 확인 (테스트 광고)**

Run: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
기기에서 확인 시나리오:
1. **설치 직후 첫 실행**: 설정을 6번 바꾸고 START → 전면광고 안 나옴 (첫 세션 가드)
2. 앱 완전 종료 후 재실행: 설정 5회 변경 → START → 테스트 전면광고 표시 → 닫으면 전광판 진입
3. 곧바로 뒤로 → 설정 5회 변경 → START → 광고 안 나옴 (2분 가드)

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/odom/ledscreen/MainActivity.kt
git commit -m "feat: interstitial on START gated by setting-change count"
```

---

### Task 6: 종료 다이얼로그 + MREC 광고

**Files:**
- Create: `app/src/main/res/layout/dialog_exit.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/odom/ledscreen/MainActivity.kt`

**Interfaces:**
- Consumes: `adsManager.exitAdView`, `adsManager.isExitAdLoaded` (Task 3)

- [ ] **Step 1: 다이얼로그 레이아웃 작성**

`app/src/main/res/layout/dialog_exit.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center_horizontal"
    android:padding="16dp">

    <TextView
        android:id="@+id/tvExitMessage"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/exit_dialog_message"
        android:textSize="18sp"
        android:textStyle="bold" />

    <FrameLayout
        android:id="@+id/exitAdContainer"
        android:layout_width="300dp"
        android:layout_height="250dp"
        android:layout_marginTop="12dp" />

</LinearLayout>
```

- [ ] **Step 2: 문자열 추가**

`strings.xml`의 `</resources>` 직전:

```xml
    <string name="exit_dialog_message">Quit LED Screen?</string>
    <string name="exit">Exit</string>
    <string name="cancel">Cancel</string>
```

- [ ] **Step 3: 뒤로가기 콜백 + 다이얼로그 구현**

`MainActivity.kt` `onCreate` 끝부분에 추가:

```kotlin
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })
```

클래스에 메서드 추가:

```kotlin
    private fun showExitDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_exit, null)
        val adContainer = content.findViewById<FrameLayout>(R.id.exitAdContainer)

        val exitAd = adsManager.exitAdView
        if (exitAd != null && adsManager.isExitAdLoaded) {
            (exitAd.parent as? ViewGroup)?.removeView(exitAd)
            adContainer.addView(exitAd)
        } else {
            adContainer.visibility = View.GONE // 광고 없으면 다이얼로그만 (블로킹 금지)
        }

        AlertDialog.Builder(this)
            .setView(content)
            .setPositiveButton(R.string.exit) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
```

import 추가: `androidx.activity.OnBackPressedCallback`, `androidx.appcompat.app.AlertDialog`, `android.view.View`, `android.view.ViewGroup`

- [ ] **Step 4: 빌드 + 수동 확인**

Run: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
기기: 메인에서 뒤로가기 → 다이얼로그에 테스트 MREC + [Exit]/[Cancel] 표시. [Cancel] → 유지, [Exit] → 종료. 비행기 모드에서 뒤로가기 → 광고 영역 없이 다이얼로그만.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/layout/dialog_exit.xml app/src/main/res/values/strings.xml app/src/main/java/com/odom/ledscreen/MainActivity.kt
git commit -m "feat: exit dialog with preloaded MREC ad"
```

---

### Task 7: 인앱 리뷰

**Files:**
- Modify: `app/src/main/java/com/odom/ledscreen/MainActivity.kt`

**Interfaces:**
- Consumes: `gatekeeper.shouldRequestReview/onReviewRequested` (Task 2), `launchResult()` (Task 5)

- [ ] **Step 1: 복귀 시 리뷰 요청 구현**

`MainActivity.kt`에 필드 추가:

```kotlin
    private var pendingReviewCheck = false
```

`launchResult()`의 `startActivity(ledIntent)` 직전에 한 줄 추가:

```kotlin
        pendingReviewCheck = true
```

클래스에 메서드 추가:

```kotlin
    override fun onResume() {
        super.onResume()
        if (pendingReviewCheck) {
            pendingReviewCheck = false
            maybeRequestReview()
        }
    }

    private fun maybeRequestReview() {
        if (!gatekeeper.shouldRequestReview()) return
        gatekeeper.onReviewRequested() // 성공 여부와 무관하게 1회만 시도
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(this, task.result)
            }
        }
    }
```

import 추가: `com.google.android.play.core.review.ReviewManagerFactory`

- [ ] **Step 2: 빌드 + 수동 확인**

Run: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
기기: 전광판 3회 사용 후 메인 복귀 시점에 리뷰 요청 호출 확인 (개발 빌드에서는 UI가 안 뜰 수 있음 — 정상. Play 내부 테스트 트랙에서 최종 확인은 Task 12).
확인 팁: `maybeRequestReview()`에 임시 `Log.d("Review", "requested")`를 넣고 logcat으로 3회째 복귀에만 찍히는지 본 뒤 제거.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/odom/ledscreen/MainActivity.kt
git commit -m "feat: in-app review after 3rd LED screen use"
```

---

### Task 8: MarqueeController + 속도 조절 (TDD)

**Files:**
- Create: `app/src/main/java/com/odom/ledscreen/MarqueeController.kt`
- Test: `app/src/test/java/com/odom/ledscreen/MarqueeControllerTest.kt`
- Modify: `app/src/main/res/layout/activity_main.xml` (속도 버튼 3개, 미리보기 TextView wrap_content)
- Modify: `app/src/main/res/layout/activity_result.xml` (TextView wrap_content + 중앙 정렬)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `MainActivity.kt`, `ResultActivity.kt`

**Interfaces:**
- Produces:
  - `enum class MarqueeSpeed(val dpPerSecond: Float) { SLOW, NORMAL, FAST }` + `MarqueeSpeed.fromName(name: String?): MarqueeSpeed`
  - `class MarqueeController` — `start(textView: TextView, container: View, direction: String, speed: MarqueeSpeed)`, `stop(textView: TextView)`, `companion fun durationMs(travelPx: Float, pxPerSecond: Float): Long`
  - Intent extra `"Speed"` (String, `MarqueeSpeed.name`)
- 전제: textView는 container 안에서 **가로 중앙 정렬**로 배치되어 있어야 함

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/odom/ledscreen/MarqueeControllerTest.kt`:

```kotlin
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
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.odom.ledscreen.MarqueeControllerTest"`
Expected: FAIL — `Unresolved reference: MarqueeController`

- [ ] **Step 3: MarqueeController 구현**

`app/src/main/java/com/odom/ledscreen/MarqueeController.kt`:

```kotlin
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.odom.ledscreen.MarqueeControllerTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: 레이아웃 수정**

(a) `activity_result.xml`의 TextView를 wrap_content + 완전 중앙 정렬로 교체:

```xml
    <TextView
        android:id="@+id/tv_result"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:ellipsize="none"
        android:singleLine="true"
        android:text="sample"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />
```

(루트 태그에 `xmlns:app="http://schemas.android.com/apk/res-auto"` 없으면 추가)

(b) `activity_main.xml`의 미리보기 `textViewNote`: `android:layout_width="match_parent"` → `android:layout_width="wrap_content"` (ll_background의 `gravity="center"`가 중앙 정렬 유지)

(c) `activity_main.xml`의 `ll_setting1`(방향/크기 버튼 줄)과 `ll_setting2` 사이에 속도 버튼 줄 추가:

```xml
            <LinearLayout
                android:id="@+id/ll_speed"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginStart="24dp"
                android:layout_marginTop="16dp"
                android:layout_marginEnd="16dp">

                <androidx.appcompat.widget.AppCompatButton
                    android:id="@+id/buttonSpeedSlow"
                    android:layout_width="0dp"
                    android:layout_weight="1"
                    android:layout_height="wrap_content"
                    android:background="@drawable/selector_background"
                    android:textColor="@color/white"
                    android:text="@string/speed_slow" />

                <androidx.appcompat.widget.AppCompatButton
                    android:id="@+id/buttonSpeedNormal"
                    android:layout_width="0dp"
                    android:layout_weight="1"
                    android:layout_height="wrap_content"
                    android:layout_marginHorizontal="8dp"
                    android:background="@drawable/selector_background"
                    android:textColor="@color/white"
                    android:text="@string/speed_normal" />

                <androidx.appcompat.widget.AppCompatButton
                    android:id="@+id/buttonSpeedFast"
                    android:layout_width="0dp"
                    android:layout_weight="1"
                    android:layout_height="wrap_content"
                    android:background="@drawable/selector_background"
                    android:textColor="@color/white"
                    android:text="@string/speed_fast" />

            </LinearLayout>
```

(d) `strings.xml`에 추가:

```xml
    <string name="speed_slow">Slow</string>
    <string name="speed_normal">Normal</string>
    <string name="speed_fast">Fast</string>
```

- [ ] **Step 6: MainActivity 배선 (미리보기 마퀴 + 속도 선택)**

`MainActivity.kt`:

(a) 필드 추가:

```kotlin
    private val previewMarquee = MarqueeController()
    private var marqueeSpeed = MarqueeSpeed.NORMAL
```

(b) `buttonLeft`/`buttonRight` 리스너 교체 (XML 애니메이션 제거):

```kotlin
        buttonLeft.setOnClickListener {
            gatekeeper.onSettingChanged()
            TextDirection = if (buttonLeft.isSelected) "STOP" else "LEFT"
            buttonLeft.isSelected = TextDirection == "LEFT"
            buttonRight.isSelected = false
            restartPreviewMarquee()
        }

        buttonRight.setOnClickListener {
            gatekeeper.onSettingChanged()
            TextDirection = if (buttonRight.isSelected) "STOP" else "RIGHT"
            buttonRight.isSelected = TextDirection == "RIGHT"
            buttonLeft.isSelected = false
            restartPreviewMarquee()
        }
```

(c) `onCreate`에 속도 버튼 배선 추가:

```kotlin
        binding.buttonSpeedSlow.setOnClickListener { selectSpeed(MarqueeSpeed.SLOW) }
        binding.buttonSpeedNormal.setOnClickListener { selectSpeed(MarqueeSpeed.NORMAL) }
        binding.buttonSpeedFast.setOnClickListener { selectSpeed(MarqueeSpeed.FAST) }
        binding.buttonSpeedNormal.isSelected = true
```

(d) 메서드 추가:

```kotlin
    private fun selectSpeed(speed: MarqueeSpeed) {
        if (marqueeSpeed != speed) gatekeeper.onSettingChanged()
        marqueeSpeed = speed
        binding.buttonSpeedSlow.isSelected = speed == MarqueeSpeed.SLOW
        binding.buttonSpeedNormal.isSelected = speed == MarqueeSpeed.NORMAL
        binding.buttonSpeedFast.isSelected = speed == MarqueeSpeed.FAST
        restartPreviewMarquee()
    }

    private fun restartPreviewMarquee() {
        when (TextDirection) {
            "LEFT", "RIGHT" -> previewMarquee.start(textViewNote, ll_background, TextDirection, marqueeSpeed)
            else -> previewMarquee.stop(textViewNote)
        }
    }
```

(e) 텍스트 입력 시 마퀴 재시작 — `onTextChanged` 안에 추가:

```kotlin
            override fun onTextChanged(s: CharSequence, start: Int,
                                       before: Int, count: Int) {
                textViewNote.text = s
                restartPreviewMarquee()
            }
```

(f) `launchResult()`에 extra 추가 (`isBlink` 줄 다음):

```kotlin
        ledIntent.putExtra("Speed", marqueeSpeed.name)
```

(g) 사용하지 않게 된 import 정리: `AnimationUtils` 관련은 buttonBlink에서 아직 사용하므로 유지 여부 확인 후 정리.

- [ ] **Step 7: ResultActivity 배선**

`ResultActivity.kt`에서 기존 마퀴 블록(48~55행: `// TODO: Text Direction`부터 `when(textDirection){...}` 끝까지)을 다음으로 교체:

```kotlin
        val speed = MarqueeSpeed.fromName(ledIntent.getStringExtra("Speed"))
        when (textDirection) {
            "LEFT", "RIGHT" -> MarqueeController().start(resultText, binding.clResult, textDirection!!, speed)
        }
```

(`animMarqueeLeft`/`animMarqueeRight` 선언 2줄 삭제. blink 블록은 유지)

- [ ] **Step 8: 빌드 + 수동 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest assembleDebug` → 전체 PASS + BUILD SUCCESSFUL
기기: 미리보기에서 좌/우/속도 3단계 동작 확인, 전광판에서 같은 속도로 끊김 없이 반복되는지, 긴 텍스트도 체감 속도가 같은지 확인.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/com/odom/ledscreen/MarqueeController.kt app/src/test/java/com/odom/ledscreen/MarqueeControllerTest.kt app/src/main/res/layout/activity_main.xml app/src/main/res/layout/activity_result.xml app/src/main/res/values/strings.xml app/src/main/java/com/odom/ledscreen/MainActivity.kt app/src/main/java/com/odom/ledscreen/ResultActivity.kt
git commit -m "feat: seamless animator-based marquee with 3-level speed control"
```

---

### Task 9: 전광판 화면 기본기 (화면유지 / 밝기 / 몰입모드 / 탭 종료)

**Files:**
- Modify: `app/src/main/java/com/odom/ledscreen/ResultActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: 없음 (독립)

- [ ] **Step 1: 문자열 추가**

`strings.xml`:

```xml
    <string name="tap_to_exit">Tap the screen to go back</string>
```

- [ ] **Step 2: ResultActivity 수정**

(a) 파일 상단의 **클래스 밖** 선언 `private lateinit var resultBackground : ConstraintLayout`을 삭제하고 클래스 안 필드로 이동 (기존 코드 스멜 수정):

```kotlin
class ResultActivity : AppCompatActivity() {

    private lateinit var resultBackground: ConstraintLayout
    private lateinit var resultText: TextView
    private lateinit var binding: ActivityResultBinding
```

(b) `onCreate`에서 deprecated 줄 `window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN` 삭제하고, `setContentView(binding.root)` 다음에 추가:

```kotlin
        // 화면 꺼짐 방지 + 표시 중 밝기 최대
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val attrs = window.attributes
        attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = attrs

        // 몰입 모드 (상태바/내비바 숨김, 스와이프 시 일시 표시)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
```

(c) `onCreate` 끝에 탭 종료 + 안내 추가:

```kotlin
        binding.clResult.setOnClickListener { finish() }
        Toast.makeText(this, R.string.tap_to_exit, Toast.LENGTH_SHORT).show()
```

(d) import 추가/정리:

```kotlin
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
```

(사용하지 않게 된 `android.view.View`, `ActivityMainBinding` import 삭제)

- [ ] **Step 3: 빌드 + 수동 확인**

Run: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
기기: 전광판 진입 시 상태바/내비바 완전 숨김, 밝기 최대, 1분 이상 방치해도 화면 유지, 화면 탭 → 메인 복귀, 복귀 후 밝기 원복 확인.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/odom/ledscreen/ResultActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: LED screen keep-on, max brightness, immersive mode, tap to exit"
```

---

### Task 10: 다크 LED 테마 폴리싱

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/drawable/selector_background.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`

**Interfaces:**
- Produces: 색 리소스 `neon_green`, `dark_surface` (Task 11 프리셋이 사용)

- [ ] **Step 1: 색 추가**

`colors.xml`의 `</resources>` 직전:

```xml
    <color name="neon_green">#39FF14</color>
    <color name="dark_surface">#1E1E1E</color>
    <color name="text_hint">#66FFFFFF</color>
```

- [ ] **Step 2: 다크 테마 강제**

`themes.xml`의 Base 스타일 교체:

```xml
    <style name="Base.Theme.Ledscreen" parent="Theme.Material3.Dark.NoActionBar">
        <item name="colorPrimary">@color/neon_green</item>
        <item name="colorOnPrimary">@color/black</item>
        <item name="android:colorBackground">@color/black</item>
        <item name="colorSurface">@color/dark_surface</item>
        <item name="colorOnSurface">@color/white</item>
    </style>
```

- [ ] **Step 3: 토글 선택 상태를 네온으로**

`drawable/selector_background.xml` 교체:

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true" android:drawable="@color/neon_green" />
    <item android:state_selected="true" android:drawable="@color/neon_green" />
    <item android:drawable="@color/dark_button_off" />
</selector>
```

- [ ] **Step 4: activity_main.xml 폴리싱**

(a) "Preview" 라벨: `android:textColor="@color/amber"` → `android:textColor="@color/neon_green"`

(b) `et_input` 스타일 교체:

```xml
        android:textColor="@color/white"
        android:textColorHint="@color/text_hint"
        android:background="@color/dark_surface"
        android:paddingHorizontal="12dp"
```

(기존 `android:textColor="@color/black"`, `android:background="@color/white"` 줄 삭제)

- [ ] **Step 5: 빌드 + 수동 확인**

Run: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
기기: 시스템 라이트 모드에서도 앱이 다크로 뜨는지, 버튼(색/Blink/START)이 네온 그린 톤인지, 방향/속도 토글 선택 시 네온 하이라이트 되는지, 색 선택 다이얼로그·종료 다이얼로그가 다크 서피스로 뜨는지 확인.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/res/values/colors.xml app/src/main/res/values/themes.xml app/src/main/res/drawable/selector_background.xml app/src/main/res/layout/activity_main.xml
git commit -m "feat: dark LED theme with neon accent and toggle highlights"
```

---

### Task 11: 프리셋 칩 + 무지개 효과

**Files:**
- Create: `app/src/main/java/com/odom/ledscreen/TextEffects.kt`
- Modify: `app/src/main/res/layout/activity_main.xml` (프리셋 칩 행)
- Modify: `MainActivity.kt`, `ResultActivity.kt`

**Interfaces:**
- Consumes: `neon_green` 색 (Task 10), `gatekeeper.onSettingChanged()` (Task 5), `restartPreviewMarquee()` (Task 8)
- Produces: `object TextEffects { fun applyRainbow(textView: TextView); fun clear(textView: TextView) }`, Intent extra `"isRainbow"` (Boolean)

- [ ] **Step 1: TextEffects 구현**

`app/src/main/java/com/odom/ledscreen/TextEffects.kt`:

```kotlin
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
```

- [ ] **Step 2: 프리셋 칩 행 레이아웃 추가**

`activity_main.xml`의 ScrollView 내부, `ll_color`(색 버튼 줄) **바로 위**에 추가:

```xml
            <HorizontalScrollView
                android:id="@+id/hsvPresets"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginStart="24dp"
                android:layout_marginTop="10dp"
                android:layout_marginEnd="16dp"
                android:scrollbars="none">

                <LinearLayout
                    android:id="@+id/llPresets"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal" />

            </HorizontalScrollView>
```

- [ ] **Step 3: MainActivity 프리셋/무지개 배선**

(a) 필드 추가:

```kotlin
    private var isRainbow = false

    /** textColor가 null이면 무지개 */
    private data class LedPreset(val label: String, val backColor: Int, val textColor: Int?)

    private val presets = listOf(
        LedPreset("Neon", R.color.black, R.color.neon_green),
        LedPreset("Fire", R.color.black, R.color.red),
        LedPreset("Ice", R.color.black, R.color.cyan),
        LedPreset("Sky", R.color.blue, R.color.white),
        LedPreset("Bee", R.color.black, R.color.yellow),
        LedPreset("Rainbow", R.color.black, null)
    )
```

(b) `onCreate`에서 `buildPresetChips()` 호출 추가 (속도 버튼 배선 다음), 메서드 구현:

```kotlin
    private fun buildPresetChips() {
        val margin = (8 * resources.displayMetrics.density).toInt()
        presets.forEach { preset ->
            val chip = Button(this)
            chip.text = preset.label
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = margin
            chip.layoutParams = lp
            chip.setOnClickListener { applyPreset(preset) }
            binding.llPresets.addView(chip)
        }
    }

    private fun applyPreset(preset: LedPreset) {
        gatekeeper.onSettingChanged()

        colorSelectorDialog1.selectedColor = preset.backColor
        ll_background.setBackgroundColor(ContextCompat.getColor(this, preset.backColor))

        isRainbow = preset.textColor == null
        if (preset.textColor != null) {
            colorSelectorDialog2.selectedColor = preset.textColor
            TextEffects.clear(textViewNote)
            textViewNote.setTextColor(ContextCompat.getColor(this, preset.textColor))
        } else {
            TextEffects.applyRainbow(textViewNote)
        }
    }
```

(c) 다이얼로그에서 일반 글자색 선택 시 무지개 해제 — `onColorClick`의 `COLOR_SELECTOR_02` 분기 안, `setTextColor` 호출 직전에:

```kotlin
                isRainbow = false
                TextEffects.clear(textViewNote)
```

(d) 텍스트 변경 시 무지개 재적용 — `onTextChanged` 안 `restartPreviewMarquee()` 앞에:

```kotlin
                if (isRainbow) TextEffects.applyRainbow(textViewNote)
```

(e) `launchResult()`에 extra 추가:

```kotlin
        ledIntent.putExtra("isRainbow", isRainbow)
```

- [ ] **Step 4: ResultActivity 무지개 적용**

`ResultActivity.kt`에서 extra 읽기 추가:

```kotlin
        val isRainbow = ledIntent.getBooleanExtra("isRainbow", false)
```

기존 `resultText.setTextColor(...)` 한 줄을 다음으로 교체 (`resultText.text = textInput` **다음**에 위치해야 함):

```kotlin
        if (isRainbow) {
            TextEffects.applyRainbow(resultText)
        } else {
            resultText.setTextColor(ContextCompat.getColor(this, textColor))
        }
```

- [ ] **Step 5: 빌드 + 수동 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest assembleDebug` → 전체 PASS + BUILD SUCCESSFUL
기기: 프리셋 칩 6개 표시·원터치 적용, Rainbow 칩 → 미리보기/전광판 모두 무지개, 무지개 상태에서 텍스트 수정해도 유지, 글자색 다이얼로그에서 색 선택하면 무지개 해제, 무지개 + 마퀴 + 깜빡임 동시 동작 확인.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/odom/ledscreen/TextEffects.kt app/src/main/res/layout/activity_main.xml app/src/main/java/com/odom/ledscreen/MainActivity.kt app/src/main/java/com/odom/ledscreen/ResultActivity.kt
git commit -m "feat: one-tap color presets and rainbow text effect"
```

---

### Task 12: 최종 검증 + 릴리스 준비

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (실제 광고 유닛 ID — 사용자 작업 필요)

- [ ] **Step 1: 전체 테스트 + 빌드**

Run: `.\gradlew.bat :app:testDebugUnitTest assembleDebug`
Expected: 12 unit tests PASS, BUILD SUCCESSFUL

- [ ] **Step 2: 통합 스모크 테스트 (기기, 체크리스트)**

- [ ] 첫 설치 실행: 배너 표시, 전면광고 절대 안 나옴
- [ ] 재실행 후 설정 5회 변경 → START → 전면광고(테스트) → 전광판 진입
- [ ] 2분 내 재시도 → 광고 없이 진입
- [ ] 뒤로가기 → MREC 종료 다이얼로그 / 비행기 모드에선 광고 없이 다이얼로그
- [ ] 전광판: 좌/우 × 속도 3단계 × 깜빡임 × 무지개 스모크, 몰입모드·밝기·화면유지·탭 종료
- [ ] 3회째 전광판 사용 후 복귀 → 리뷰 플로우 호출 (logcat)
- [ ] 화면 회전(메인) 시 크래시 없음, 색 상태 복원

- [ ] **Step 3: 실제 광고 유닛으로 교체 (사용자 작업 필요 — 차단점)**

AdMob 콘솔(앱 `ca-app-pub-6729344454320392~4373024775`)에서 **전면광고 유닛**과 **MREC(배너) 유닛**을 새로 생성한 뒤, `strings.xml`의 `TEST_interstitial_ad_unit_id`, `TEST_banner_ad_unit_id` 값을 실제 유닛 ID로 교체하고 개발용 주석 삭제. **이 단계는 AdMob 콘솔 접근이 필요하므로 사용자에게 유닛 ID를 요청할 것.**

- [ ] **Step 4: 릴리스 빌드**

Run: `.\gradlew.bat assembleRelease`
Expected: BUILD SUCCESSFUL. `versionCode 5 / versionName "1.3"` 확인.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/values/strings.xml
git commit -m "release: real ad unit ids for v1.3"
```

- [ ] **Step 6: 릴리스 후속 안내 (문서화만)**

Play Console 내부 테스트 트랙 업로드 → 인앱리뷰 실제 표시 확인 → 프로덕션 승격. (수동 작업, 계획 범위 밖)
