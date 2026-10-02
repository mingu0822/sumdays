package com.example.sumdays.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds

object AdMobInitializer {
    private val lock = Any()
    private val callbacks = mutableListOf<(Boolean) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var initialized = false
    private var initializing = false

    fun initialize(context: Context, onInitialized: ((Boolean) -> Unit)? = null) {
        synchronized(lock) {
            if (initialized) {
                onInitialized?.let { callback -> mainHandler.post { callback(true) } }
                return
            }
            onInitialized?.let(callbacks::add)
            if (initializing) return
            initializing = true
        }

        val appContext = context.applicationContext
        mainHandler.post {
            try {
                MobileAds.initialize(appContext) {
                    finishInitialization(true)
                }
            } catch (_: Exception) {
                finishInitialization(false)
            }
        }
    }

    private fun finishInitialization(success: Boolean) {
        val pendingCallbacks = synchronized(lock) {
            initialized = success
            initializing = false
            callbacks.toList().also { callbacks.clear() }
        }
        pendingCallbacks.forEach { callback ->
            mainHandler.post { callback(success) }
        }
    }
}
