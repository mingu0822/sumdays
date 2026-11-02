package com.example.sumdays.review

import com.example.sumdays.data.sync.InitialSyncRetryPolicy
import org.junit.Assert.*
import org.junit.Test

class InitialSyncRetryPolicyTest {
    @Test fun `temporary HTTP failures retry but auth and invalid requests stop`() {
        listOf(408, 429, 500, 502, 503, 504).forEach { assertTrue(InitialSyncRetryPolicy.shouldRetry(it)) }
        listOf(200, 400, 401, 403, 404).forEach { assertFalse(InitialSyncRetryPolicy.shouldRetry(it)) }
    }
}
