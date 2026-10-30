package com.example.sumdays.customize

import android.content.Context
import android.graphics.Bitmap

/** All fox images use the same account-owned preview directory. */
object FoxImageStorage {
    fun save(context: Context, bitmap: Bitmap, foxId: Int): String =
        FoxBitmapRenderer.savePreview(context, foxId, bitmap)

    fun load(context: Context, path: String): Bitmap? =
        FoxBitmapRenderer.loadPreview(context, path)

    fun delete(context: Context, path: String) =
        FoxBitmapRenderer.deletePreview(context, path)
}
