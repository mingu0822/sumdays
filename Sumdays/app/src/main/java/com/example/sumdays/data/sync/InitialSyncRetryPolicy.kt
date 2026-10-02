package com.example.sumdays.data.sync

/** Temporary connectivity/server-load errors must not end initial sync. */
object InitialSyncRetryPolicy {
    fun shouldRetry(status: Int): Boolean = status == 408 || status == 429 || status >= 500
}
