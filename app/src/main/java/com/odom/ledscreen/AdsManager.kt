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
    private var bannerAdView: AdView? = null
    private var isLoadingInterstitial = false

    var exitAdView: AdView? = null
        private set
    var isExitAdLoaded = false
        private set

    val isInterstitialReady: Boolean get() = interstitialAd != null

    /** UMP 동의 확인 후 SDK 초기화. onAdsAvailable은 메인 스레드에서 호출된다(UMP/GMA가 메인 스레드로 전달); 호출부의 runOnUiThread는 방어적 처리다. */
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
        bannerAdView?.destroy()
        val adView = AdView(activity)
        adView.adUnitId = activity.getString(R.string.TEST_banner_ad_unit_id)
        adView.setAdSize(adaptiveAdSize())
        container.removeAllViews()
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
        bannerAdView = adView
    }

    private fun adaptiveAdSize(): AdSize {
        val metrics: DisplayMetrics = activity.resources.displayMetrics
        val adWidth = (metrics.widthPixels / metrics.density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    // ---- 전면광고 ----

    fun loadInterstitial() {
        if (interstitialAd != null || isLoadingInterstitial) return
        isLoadingInterstitial = true
        InterstitialAd.load(
            activity,
            activity.getString(R.string.TEST_interstitial_ad_unit_id),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoadingInterstitial = false
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
        bannerAdView?.destroy()
        bannerAdView = null
        exitAdView?.destroy()
        exitAdView = null
        isExitAdLoaded = false
    }
}
