package com.example.sumdays.stickers

import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.sync.buildSyncRequest
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.util.UUID

interface StickerRemote {
    suspend fun list(owner: Int, date: String): List<DiarySticker>
    suspend fun place(request: PendingSticker): DiarySticker
    suspend fun update(sticker: DiarySticker, geometry: StickerGeometry): DiarySticker
    suspend fun remove(sticker: DiarySticker)
    suspend fun syncOwnDiary(entry: DailyEntry)
}

class RetrofitStickerRemote(token: String) : StickerRemote {
    private val authorization = "Bearer $token"

    private fun <T> read(response: Response<ApiResponse<T>>): T {
        if (response.isSuccessful && response.body()?.success == true) {
            return response.body()?.data ?: throw IOException("스티커 응답이 올바르지 않아요.")
        }
        val error = runCatching { JSONObject(response.errorBody()?.string().orEmpty()) }.getOrNull()
        throw StickerRequestException(response.code(), error?.optString("code").orEmpty(),
            error?.optString("message")?.takeIf { it.isNotBlank() } ?: "스티커를 불러오지 못했어요. 다시 시도해 주세요.")
    }

    override suspend fun list(owner: Int, date: String) = read(ApiClient.stickerApi.list(authorization, owner, date))
    override suspend fun place(request: PendingSticker) = read(ApiClient.stickerApi.place(authorization,
        request.ownerId, request.date, request.id, PlaceStickerBody(request)))
    override suspend fun update(sticker: DiarySticker, geometry: StickerGeometry) = read(ApiClient.stickerApi.update(
        authorization, sticker.ownerId, sticker.date, sticker.id, geometry))
    override suspend fun remove(sticker: DiarySticker) {
        read(ApiClient.stickerApi.remove(authorization, sticker.ownerId, sticker.date, sticker.id))
    }
    override suspend fun syncOwnDiary(entry: DailyEntry) {
        val request = buildSyncRequest(emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), listOf(entry), emptyList())
        val result = ApiClient.api.syncData(authorization, request)
        if (!result.isSuccessful || result.body()?.status != "success") {
            throw IOException("일기를 서버에 저장하지 못했어요. 연결을 확인하고 다시 시도해 주세요.")
        }
    }
}

class StickerRepository(
    private val inventory: StickerInventoryStore,
    private val remote: StickerRemote,
    private val isCurrentAccount: () -> Boolean
) {
    private fun requireAccount() { check(isCurrentAccount()) { "계정이 변경되었어요. 일기를 다시 열어 주세요." } }

    suspend fun load(owner: Int, date: String): List<DiarySticker> = withContext(Dispatchers.IO) {
        requireAccount()
        // Resolve receipts before allowing another purchase to be spent, even after app restart.
        for (request in inventory.pending()) {
            requireAccount()
            try { sendReserved(request) } catch (error: StickerRequestException) {
                if (!error.definitelyRejected) throw error
            }
        }
        requireAccount()
        remote.list(owner, date).filter { it.active && it.geometry().isValid() }
    }

    suspend fun place(owner: Int, date: String, stickerId: Int, geometry: StickerGeometry,
                      ownEntry: DailyEntry?): DiarySticker = withContext(Dispatchers.IO) {
        requireAccount()
        require(geometry.isValid())
        check(inventory.pending().isEmpty()) { "이전 스티커 저장을 확인하고 있어요. 잠시 후 다시 시도해 주세요." }
        if (ownEntry != null) {
            require(ownEntry.date == date && !ownEntry.diary.isNullOrBlank() && !ownEntry.isDeleted)
            remote.syncOwnDiary(ownEntry)
        }
        requireAccount()
        val request = PendingSticker(UUID.randomUUID().toString(), owner, date, stickerId, geometry)
        withContext(NonCancellable) {
            check(inventory.reserve(request)) { "보유한 스티커가 부족해요." }
            sendReserved(request)
        }
    }

    private suspend fun sendReserved(request: PendingSticker): DiarySticker = withContext(NonCancellable) {
        try {
            val result = remote.place(request)
            inventory.finish(request.id, accepted = true)
            result
        } catch (error: StickerRequestException) {
            if (error.definitelyRejected) inventory.finish(request.id, accepted = false)
            throw error
        }
        // Timeouts, cancellation and server failures retain the same request ID and reservation.
    }

    suspend fun update(sticker: DiarySticker, geometry: StickerGeometry) = withContext(Dispatchers.IO) {
        requireAccount()
        require(geometry.isValid())
        remote.update(sticker, geometry)
    }

    suspend fun remove(sticker: DiarySticker) = withContext(Dispatchers.IO) {
        requireAccount()
        remote.remove(sticker)
    }
}
