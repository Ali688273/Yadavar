package com.yadavar.app.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.yadavar.app.R
import ir.tapsell.plus.AdHolder
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryAdListener
import com.adivery.sdk.AdiveryListener
import com.adivery.sdk.AdiveryBannerAdView
import com.adivery.sdk.BannerSize
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.AdShowListener
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusBannerType
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusErrorModel

object YadavarAds {
    private const val TAG = "YadavarAds"

    private const val TAPSELL_KEY = "fqrlspoqrophiiakrpntbhnkgpsmibkobapsrnbjkdbjeassfgmqmcmhmqpqneqaknfqgr"
    const val TAPSELL_REWARDED = "6abc9adf3c315037c8c55d1f"
    const val TAPSELL_INTERSTITIAL = "6abc9b023c315037c8c55d20"
    const val TAPSELL_PREVIEW_VIDEO = "6abc9b213c315037c8c55d21"
    const val TAPSELL_NATIVE_VIDEO = "6abc9b493c315037c8c55d22"
    const val TAPSELL_BANNER = "6abc9b683c315037c8c55d23"
    const val TAPSELL_INSTANT_BANNER = "6abc9b913c315037c8c55d24"
    const val TAPSELL_NATIVE_BANNER = "6abc9bb33c315037c8c55d25"

    const val ADIVERY_APP_ID = "110864ca-c2ab-4fb4-8a6d-69c27dbc313b"
    const val ADIVERY_INTERSTITIAL = "47ec8b2b-44b0-432f-8c85-5e5c27954bfa"
    const val ADIVERY_REWARDED = "34f5699b-da60-4495-9b0c-77bdbf027243"
    const val ADIVERY_BANNER = "3ab23d87-e55c-48a9-adc7-e2d41ab59833"
    const val ADIVERY_NATIVE = "e25dc60d-6642-4426-9e05-4e50cc871e4e"
    const val ADIVERY_APP_OPEN = "c6b5ef7a-2ba0-4174-96ad-4463b2dddd0b"
    const val ADIVERY_PREVIEW_VIDEO = "b3a92466-9bb3-44e0-ae40-24c5d340768c"

    @Volatile private var tapsellReady = false
    @Volatile private var initialized = false
    private var lastInterstitialAt = 0L
    private var completionCount = 0
    private const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 60 * 1000L
    private const val INTERSTITIAL_EVERY_COMPLETIONS = 8

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        Adivery.setLoggingEnabled(false)
        Adivery.configure(context.applicationContext, ADIVERY_APP_ID)
        TapsellPlus.initialize(context.applicationContext, TAPSELL_KEY, object : TapsellPlusInitListener {
            override fun onInitializeSuccess(adNetworks: AdNetworks) {
                tapsellReady = true
                Log.d(TAG, "TapsellPlus initialized")
            }

            override fun onInitializeFailed(adNetworks: AdNetworks, adNetworkError: AdNetworkError) {
                Log.w(TAG, "TapsellPlus initialization failed: " + adNetworkError.errorMessage)
            }
        })
    }

    fun maybeShowInterstitial(activity: Activity) {
        completionCount++
        if (completionCount % INTERSTITIAL_EVERY_COMPLETIONS != 0) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastInterstitialAt < INTERSTITIAL_COOLDOWN_MS) return
        lastInterstitialAt = now
        showInterstitial(activity)
    }

    fun showInterstitial(activity: Activity) {
        if (tapsellReady) {
            TapsellPlus.requestInterstitialAd(activity, TAPSELL_INTERSTITIAL, object : AdRequestCallback() {
                override fun response(ad: TapsellPlusAdModel) {
                    TapsellPlus.showInterstitialAd(activity, ad.responseId, object : AdShowListener() {})
                }

                override fun error(message: String) {
                    showAdiveryInterstitial(activity)
                }
            })
        } else {
            showAdiveryInterstitial(activity)
        }
    }

    private fun showAdiveryInterstitial(activity: Activity) {
        if (Adivery.isLoaded(ADIVERY_INTERSTITIAL)) {
            Adivery.showAd(ADIVERY_INTERSTITIAL)
        } else {
            val listener = object : AdiveryAdListener() {
                override fun onAdLoaded() {
                    Adivery.showAd(ADIVERY_INTERSTITIAL)
                    Adivery.removePlacementListener(ADIVERY_INTERSTITIAL)
                }
                override fun onAdShown() = Unit
                override fun onAdClicked() = Unit
                override fun onError(reason: String) {
                    Adivery.removePlacementListener(ADIVERY_INTERSTITIAL)
                }
            }
            Adivery.addPlacementListener(ADIVERY_INTERSTITIAL, listener)
            Adivery.prepareInterstitialAd(activity, ADIVERY_INTERSTITIAL)
        }
    }

    fun showRewarded(activity: Activity, onRewarded: () -> Unit) {
        if (tapsellReady) {
            TapsellPlus.requestRewardedVideoAd(activity, TAPSELL_REWARDED, object : AdRequestCallback() {
                override fun response(ad: TapsellPlusAdModel) {
                    TapsellPlus.showRewardedVideoAd(activity, ad.responseId, object : AdShowListener() {
                        override fun onRewarded(ad: TapsellPlusAdModel) {
                            onRewarded()
                        }
                        override fun onError(error: TapsellPlusErrorModel) {
                            showAdiveryRewarded(activity, onRewarded)
                        }
                    })
                }

                override fun error(message: String) {
                    showAdiveryRewarded(activity, onRewarded)
                }
            })
        } else {
            showAdiveryRewarded(activity, onRewarded)
        }
    }

    private fun showAdiveryRewarded(activity: Activity, onRewarded: () -> Unit) {
        if (Adivery.isLoaded(ADIVERY_REWARDED)) {
            installRewardListener(activity, onRewarded)
            Adivery.showAd(ADIVERY_REWARDED)
        } else {
            val listener = object : AdiveryAdListener() {
                override fun onAdLoaded() {
                    installRewardListener(activity, onRewarded)
                    Adivery.showAd(ADIVERY_REWARDED)
                    Adivery.removePlacementListener(ADIVERY_REWARDED)
                }
                override fun onAdShown() = Unit
                override fun onAdClicked() = Unit
                override fun onError(reason: String) {
                    Adivery.removePlacementListener(ADIVERY_REWARDED)
                }
            }
            Adivery.addPlacementListener(ADIVERY_REWARDED, listener)
            Adivery.prepareRewardedAd(activity, ADIVERY_REWARDED)
        }
    }

    private fun installRewardListener(activity: Activity, onRewarded: () -> Unit) {
        val listener = object : AdiveryListener() {
            override fun onRewardedAdLoaded(placementId: String) = Unit
            override fun onRewardedAdShown(placementId: String) = Unit
            override fun onRewardedAdClicked(placementId: String) = Unit
            override fun onRewardedAdClosed(placementId: String, isRewarded: Boolean) {
                Adivery.removePlacementListener(ADIVERY_REWARDED)
                if (isRewarded) onRewarded()
            }
            override fun log(placementId: String, message: String) = Unit
        }
        Adivery.addPlacementListener(ADIVERY_REWARDED, listener)
    }

    private var lastAppOpenAt = 0L

    fun maybeShowAppOpen(activity: Activity, backgroundDurationMs: Long) {
        if (backgroundDurationMs < 60_000L) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAppOpenAt < 2 * 60 * 60 * 1000L) return
        lastAppOpenAt = now
        if (Adivery.isLoaded(ADIVERY_APP_OPEN)) {
            Adivery.showAppOpenAd(activity, ADIVERY_APP_OPEN)
        } else {
            val listener = object : AdiveryListener() {
                override fun onAppOpenAdLoaded(placementId: String) {
                    Adivery.showAppOpenAd(activity, placementId)
                    Adivery.removePlacementListener(ADIVERY_APP_OPEN)
                }
                override fun onAppOpenAdShown(placementId: String) = Unit
                override fun onAppOpenAdClicked(placementId: String) = Unit
                override fun onAppOpenAdClosed(placementId: String) {
                    Adivery.removePlacementListener(ADIVERY_APP_OPEN)
                }
                override fun log(placementId: String, message: String) = Unit
            }
            Adivery.addPlacementListener(ADIVERY_APP_OPEN, listener)
            Adivery.prepareAppOpenAd(activity, ADIVERY_APP_OPEN)
        }
    }

    fun loadTapsellBanner(context: Context, container: FrameLayout, onError: () -> Unit) {
        val activity = context as? Activity ?: run { onError(); return }
        if (!tapsellReady) {
            onError()
            return
        }
        TapsellPlus.requestStandardBannerAd(
            activity,
            TAPSELL_BANNER,
            TapsellPlusBannerType.BANNER_320x50,
            object : AdRequestCallback() {
                override fun response(ad: TapsellPlusAdModel) {
                    TapsellPlus.showStandardBannerAd(
                        activity,
                        ad.responseId,
                        container,
                        object : AdShowListener() {
                            override fun onError(error: TapsellPlusErrorModel) {
                                onError()
                            }
                        }
                    )
                }

                override fun error(message: String) {
                    onError()
                }
            }
        )
    }

    fun createAdiveryNative(context: Context, onError: () -> Unit): com.adivery.sdk.AdiveryNativeAdView =
        com.adivery.sdk.AdiveryNativeAdView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPlacementId(ADIVERY_NATIVE)
            setNativeAdLayout(R.layout.view_yadavar_native_ad)
            setListener(object : AdiveryAdListener() {
                override fun onAdLoaded() = Unit
                override fun onAdShown() = Unit
                override fun onAdClicked() = Unit
                override fun onError(reason: String) = onError()
            })
        }

    fun loadTapsellNativeBanner(context: Context, container: FrameLayout, onError: () -> Unit) {
        val activity = context as? Activity ?: run { onError(); return }
        if (!tapsellReady) {
            onError()
            return
        }
        val holder: AdHolder = TapsellPlus.createAdHolder(
            activity,
            container,
            ir.tapsell.plus.R.layout.native_banner
        )
        TapsellPlus.requestNativeAd(activity, TAPSELL_NATIVE_BANNER, object : AdRequestCallback() {
            override fun response(ad: TapsellPlusAdModel) {
                TapsellPlus.showNativeAd(activity, ad.responseId, holder, object : AdShowListener() {
                    override fun onError(error: TapsellPlusErrorModel) = onError()
                })
            }
            override fun error(message: String) = onError()
        })
    }

    fun createAdiveryBanner(context: Context, onError: () -> Unit): AdiveryBannerAdView =
        AdiveryBannerAdView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPlacementId(ADIVERY_BANNER)
            setBannerSize(BannerSize.BANNER)
            setBannerAdListener(object : AdiveryAdListener() {
                override fun onAdLoaded() = Unit
                override fun onAdShown() = Unit
                override fun onAdClicked() = Unit
                override fun onError(reason: String) = onError()
            })
        }
}
