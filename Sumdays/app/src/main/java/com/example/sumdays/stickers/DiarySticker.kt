package com.example.sumdays.stickers

data class StickerGeometry(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.18f,
    val rotation: Float = 0f
) {
    fun isValid() = x.isFinite() && y.isFinite() && size.isFinite() && rotation.isFinite() &&
        x in 0f..1f && y in 0f..1f && size in 0.08f..0.35f && rotation in -180f..180f
}

data class DiarySticker(
    val id: String,
    val ownerId: Int,
    val authorId: Int,
    val authorName: String,
    val date: String,
    val stickerId: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val active: Boolean = true
) {
    fun geometry() = StickerGeometry(x, y, size, rotation)
    fun withGeometry(value: StickerGeometry) = copy(x = value.x, y = value.y, size = value.size, rotation = value.rotation)
}

data class PendingSticker(
    val id: String,
    val ownerId: Int,
    val date: String,
    val stickerId: Int,
    val geometry: StickerGeometry
)

data class PlaceStickerBody(val stickerId: Int, val x: Float, val y: Float, val size: Float, val rotation: Float) {
    constructor(pending: PendingSticker) : this(pending.stickerId, pending.geometry.x,
        pending.geometry.y, pending.geometry.size, pending.geometry.rotation)
}

class StickerRequestException(val status: Int, val code: String, message: String) : Exception(message) {
    // These responses are returned only before a placement is committed (or for a conflicting ID).
    val definitelyRejected: Boolean get() = code in setOf("STICKER_INVALID", "STICKER_DIARY_MISSING",
        "STICKER_FORBIDDEN", "STICKER_CONFLICT", "STICKER_LIMIT") && status in 400..499
}
