package com.example.sumdays.stickers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.data.DailyEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StickerRepositoryTest {
    private lateinit var context: Context
    private lateinit var stock: StickerInventoryStore
    private lateinit var remote: FakeRemote
    private var current = true
    private fun repository() = StickerRepository(stock, remote) { current }
    private val diary = DailyEntry("2026-10-11", "오늘은 산책을 했어요.", null, null, null, null, null)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        for (scope in listOf("user_101", "user_202")) {
            context.getSharedPreferences("sticker_inventory__$scope", Context.MODE_PRIVATE).edit().clear().commit()
        }
        stock = StickerInventoryStore(context, "user_101")
        stock.add(1001)
        remote = FakeRemote()
        current = true
    }

    @Test fun `confirmed placement consumes once and edits and removal do not consume again`() = runBlocking {
        val repository = repository()
        val saved = repository.place(101, diary.date, 1001, StickerGeometry(), diary)
        assertEquals(0, stock.count(1001))
        assertTrue(stock.pending().isEmpty())
        assertEquals(1, remote.synced.size)
        assertEquals(saved, repository.load(101, diary.date).single())
        val changed = repository.update(saved, saved.geometry().copy(x = 0.9f, rotation = 45f))
        assertEquals(0.9f, changed.x)
        repository.remove(changed)
        assertTrue(repository.load(101, diary.date).isEmpty())
        assertEquals(0, stock.count(1001))
    }

    @Test fun `response lost after commit recovers same ID after reopening without duplicate debit`() = runBlocking {
        remote.loseResponse = true
        try { repository().place(202, diary.date, 1001, StickerGeometry(), null); fail() } catch (_: IOException) {}
        assertEquals(0, stock.count(1001))
        val pending = stock.pending().single()
        assertEquals(pending.id, remote.rows.keys.single())
        stock = StickerInventoryStore(context, "user_101")
        val restored = repository().load(202, diary.date)
        assertEquals(pending.id, restored.single().id)
        assertEquals(1, remote.rows.size)
        assertEquals(0, stock.count(1001))
        assertTrue(stock.pending().isEmpty())
        assertEquals(listOf(pending.id, pending.id), remote.requests.map { it.id })
    }

    @Test fun `definitive permission rejection restores reserved stock once`() = runBlocking {
        remote.rejection = StickerRequestException(403, "STICKER_FORBIDDEN", "비공개")
        try { repository().place(202, diary.date, 1001, StickerGeometry(), null); fail() } catch (_: StickerRequestException) {}
        assertEquals(1, stock.count(1001))
        assertTrue(stock.pending().isEmpty())
        stock.finish(remote.requests.single().id, accepted = false)
        assertEquals(1, stock.count(1001))
    }

    @Test fun `server failures preserve reservations and prohibit spending again until resolved`() = runBlocking {
        stock.add(1001)
        remote.rejection = StickerRequestException(500, "STICKER_SERVER", "서버 오류")
        try { repository().place(202, diary.date, 1001, StickerGeometry(), null); fail() } catch (_: StickerRequestException) {}
        assertEquals(1, stock.count(1001))
        assertEquals(1, stock.pending().size)
        try { repository().place(202, diary.date, 1001, StickerGeometry(), null); fail() } catch (_: IllegalStateException) {}
        assertEquals(1, remote.requests.size)
        remote.rejection = null
        assertEquals(1, repository().load(202, diary.date).size)
        assertTrue(stock.pending().isEmpty())
        assertEquals(1, stock.count(1001))
    }

    @Test fun `an uncertain retry rejected before commit refunds while loading another diary`() = runBlocking {
        val request = PendingSticker(UUID.randomUUID().toString(), 202, diary.date, 1001, StickerGeometry())
        assertTrue(stock.reserve(request))
        remote.rejection = StickerRequestException(404, "STICKER_DIARY_MISSING", "삭제됨")
        assertTrue(repository().load(101, diary.date).isEmpty())
        assertEquals(1, stock.count(1001))
        assertTrue(stock.pending().isEmpty())
    }

    @Test fun `empty stock or account switch prevents placement`() = runBlocking {
        stock.finish("not-present", accepted = false)
        try { repository().place(202, diary.date, 1002, StickerGeometry(), null); fail() } catch (_: IllegalStateException) {}
        assertTrue(remote.requests.isEmpty())
        current = false
        try { repository().place(202, diary.date, 1001, StickerGeometry(), null); fail() } catch (_: IllegalStateException) {}
        assertEquals(1, stock.count(1001))
        assertTrue(remote.requests.isEmpty())
    }

    @Test fun `late response settles the original account while a different account keeps its stock`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        remote.beforePlace = { started.complete(Unit); complete.await() }
        val saving = async { repository().place(202, diary.date, 1001, StickerGeometry(), null) }
        started.await()
        current = false
        val other = StickerInventoryStore(context, "user_202")
        other.add(1001); other.add(1001)
        complete.complete(Unit)
        saving.await()
        assertEquals(2, other.count(1001))
        assertEquals(0, stock.count(1001))
        assertTrue(stock.pending().isEmpty())
    }

    @Test fun `activity cancellation after request starts still settles a successful receipt`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        remote.beforePlace = { started.complete(Unit); complete.await() }
        val saving = launch { repository().place(202, diary.date, 1001, StickerGeometry(), null) }
        started.await()
        saving.cancel()
        complete.complete(Unit)
        saving.join()
        assertEquals(0, stock.count(1001))
        assertTrue(stock.pending().isEmpty())
        assertEquals(1, remote.rows.size)
    }

    @Test fun `switching accounts during own diary upload does not reserve stock or send placement`() = runBlocking {
        remote.beforeSync = { current = false }
        try { repository().place(101, diary.date, 1001, StickerGeometry(), diary); fail() } catch (_: IllegalStateException) {}
        assertEquals(1, stock.count(1001))
        assertTrue(remote.requests.isEmpty())
    }

    @Test fun `failed diary upload and invalid geometry cannot debit inventory`() = runBlocking {
        remote.beforeSync = { throw IOException("Offline") }
        try { repository().place(101, diary.date, 1001, StickerGeometry(), diary); fail() } catch (_: IOException) {}
        try { repository().place(202, diary.date, 1001, StickerGeometry(x = Float.NaN), null); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(1, stock.count(1001))
        assertTrue(remote.requests.isEmpty())
    }

    @Test fun `repeated reservation of the same request ID does not debit twice`() {
        val request = PendingSticker(UUID.randomUUID().toString(), 202, diary.date, 1001, StickerGeometry())
        assertTrue(stock.reserve(request))
        assertTrue(stock.reserve(request))
        assertEquals(0, stock.count(1001))
        assertEquals(1, stock.pending().size)
        stock.finish(request.id, accepted = false)
        stock.finish(request.id, accepted = false)
        assertEquals(1, stock.count(1001))
    }

    private class FakeRemote : StickerRemote {
        val rows = linkedMapOf<String, DiarySticker>()
        val requests = mutableListOf<PendingSticker>()
        val synced = mutableListOf<DailyEntry>()
        var loseResponse = false
        var rejection: StickerRequestException? = null
        var beforePlace: suspend () -> Unit = {}
        var beforeSync: suspend () -> Unit = {}
        override suspend fun list(owner: Int, date: String) = rows.values.filter { it.ownerId == owner && it.date == date && it.active }
        override suspend fun place(request: PendingSticker): DiarySticker {
            requests += request
            beforePlace()
            rejection?.let { throw it }
            val result = rows.getOrPut(request.id) { with(request) { DiarySticker(id, ownerId, 101, "나", date,
                stickerId, geometry.x, geometry.y, geometry.size, geometry.rotation) } }
            if (loseResponse) { loseResponse = false; throw IOException("Response lost after commit") }
            return result
        }
        override suspend fun update(sticker: DiarySticker, geometry: StickerGeometry) = sticker.withGeometry(geometry).also { rows[it.id] = it }
        override suspend fun remove(sticker: DiarySticker) { rows[sticker.id] = sticker.copy(active = false) }
        override suspend fun syncOwnDiary(entry: DailyEntry) { beforeSync(); synced += entry }
    }
}
