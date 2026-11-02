package com.example.sumdays.review

import com.example.sumdays.daily.memo.*
import com.example.sumdays.data.Memo
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoMergeCoordinatorTest {
    private fun memos() = mutableListOf(
        Memo(1, "first", "10:00", "2026-09-13", 0),
        Memo(2, "second", "11:00", "2026-09-13", 1),
        Memo(3, "third", "12:00", "2026-09-13", 2),
    )

    private class FakeGateway : MemoMergeGateway {
        var fail = false
        var calls = 0
        var lastPayload = emptyList<MemoPayload>()
        override suspend fun merge(payloads: List<MemoPayload>, endFlag: Boolean, onPartial: suspend (String) -> Unit): MergeResult {
            calls++
            lastPayload = payloads
            onPartial("partial")
            delay(100)
            if (fail) error("simulated failure")
            return MergeResult("merged", "mood")
        }
        override suspend fun generateMood(diary: String) = "mood"
    }

    @Test fun `merge undo final diary and overlapping drag are coordinated`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val list = memos()
            val original = list.toList()
            val fake = FakeGateway()
            var refreshed = 0
            val coordinator = MemoMergeCoordinator(list, this, fake, {}, {}, { fail(it) }, { refreshed++ })
            coordinator.merge(0, 1)
            coordinator.merge(1, 2) // ignored while first request is in flight
            assertFalse(coordinator.undoLastMerge())
            advanceUntilIdle()
            assertEquals(1, fake.calls)
            assertEquals(listOf("second", "first"), fake.lastPayload.map { it.content })
            assertEquals(listOf("merged", "third"), list.map { it.content })
            assertEquals(listOf(2, 3), list.map { it.id })
            assertTrue(coordinator.undoLastMerge())
            assertEquals(original, list)
            assertFalse(coordinator.undoLastMerge())
            assertEquals("merged", coordinator.mergeAll())
            assertEquals("mood", coordinator.lastMood)
            assertEquals(2, refreshed)
            assertFalse(coordinator.isMerging)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `failure after partial response restores list and allows another merge`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val list = memos()
            val original = list.toList()
            val fake = FakeGateway().apply { fail = true }
            val errors = mutableListOf<String>()
            val coordinator = MemoMergeCoordinator(list, this, fake, {}, {}, { errors.add(it) }, {})
            coordinator.merge(2, 0)
            runCurrent()
            assertEquals("partial", list[0].content)
            advanceUntilIdle()
            assertEquals(original, list)
            assertEquals(listOf("simulated failure"), errors)
            assertFalse(coordinator.isMerging)
            assertFalse(coordinator.undoLastMerge())
            fake.fail = false
            coordinator.merge(2, 0)
            advanceUntilIdle()
            assertEquals(listOf("merged", "second"), list.map { it.content })
            assertTrue(coordinator.undoLastMerge())
            assertEquals(original, list)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `cancellation restores partial UI without reporting an error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.Main)
            val list = memos()
            val original = list.toList()
            val coordinator = MemoMergeCoordinator(list, scope, FakeGateway(), {}, {}, { fail(it) }, {})
            coordinator.merge(0, 2)
            runCurrent()
            job.cancel()
            advanceUntilIdle()
            assertEquals(original, list)
            assertFalse(coordinator.isMerging)
        } finally { Dispatchers.resetMain() }
    }
}
