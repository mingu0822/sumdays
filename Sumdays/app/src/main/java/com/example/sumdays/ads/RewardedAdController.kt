package com.example.sumdays.ads

import android.app.Activity
import android.os.SystemClock
import com.example.sumdays.auth.SessionManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import java.util.UUID

enum class RewardedAdState {
    INITIALIZING,
    LOADING,
    READY,
    SHOWING,
    FAILED,
}

class RewardedAdController(
    private val activity: Activity,
    private val adUnitId: String,
    private val onStateChanged: (RewardedAdState) -> Unit,
) {
    private var rewardedAd: RewardedAd? = null
    private var state = RewardedAdState.INITIALIZING
    private var loadedAtElapsedTime = 0L
    private var destroyed = false

    fun currentState(): RewardedAdState = state

    fun load() {
        if (destroyed || state == RewardedAdState.LOADING || state == RewardedAdState.SHOWING) return
        if (isReady()) {
            updateState(RewardedAdState.READY)
            return
        }

        updateState(RewardedAdState.INITIALIZING)
        AdMobInitializer.initialize(activity.applicationContext) { initialized ->
            if (destroyed || activity.isFinishing || activity.isDestroyed) return@initialize
            if (initialized) {
                loadAfterInitialization()
            } else {
                updateState(RewardedAdState.FAILED)
            }
        }
    }

    fun show(
        onRewardEarned: (rewardId: String) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        if (!isReady()) {
            rewardedAd = null
            load()
            onUnavailable()
            return
        }

        val ad = rewardedAd ?: run {
            onUnavailable()
            return
        }
        val rewardId = UUID.randomUUID().toString()
        var rewardDelivered = false

        ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder()
            .setUserId(SessionManager.getUserId().toString())
            .setCustomData(rewardId)
            .build())
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                updateState(RewardedAdState.SHOWING)
            }

            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                updateState(RewardedAdState.INITIALIZING)
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                updateState(RewardedAdState.FAILED)
                onUnavailable()
                load()
            }
        }

        rewardedAd = null
        updateState(RewardedAdState.SHOWING)
        try {
            ad.show(activity) {
                if (!rewardDelivered) {
                    rewardDelivered = true
                    onRewardEarned(rewardId)
                }
            }
        } catch (_: Exception) {
            updateState(RewardedAdState.FAILED)
            onUnavailable()
            load()
        }
    }

    fun destroy() {
        destroyed = true
        rewardedAd = null
    }

    private fun loadAfterInitialization() {
        if (destroyed || state == RewardedAdState.LOADING || state == RewardedAdState.SHOWING) return
        updateState(RewardedAdState.LOADING)
        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (destroyed) return
                    rewardedAd = ad
                    loadedAtElapsedTime = SystemClock.elapsedRealtime()
                    updateState(RewardedAdState.READY)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    updateState(RewardedAdState.FAILED)
                }
            },
        )
    }

    private fun isReady(): Boolean {
        val age = SystemClock.elapsedRealtime() - loadedAtElapsedTime
        return rewardedAd != null && age in 0 until AD_EXPIRATION_MILLIS
    }

    private fun updateState(newState: RewardedAdState) {
        state = newState
        onStateChanged(newState)
    }

    private companion object {
        const val AD_EXPIRATION_MILLIS = 55L * 60L * 1000L
    }
}
