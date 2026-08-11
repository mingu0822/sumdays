package com.example.sumdays.customize

import android.content.Context
import android.graphics.Bitmap
import java.io.File

object FoxImageStorage {

    private const val FOLDER_NAME = "foxes"

    fun save(
        context: Context,
        bitmap: Bitmap,
        foxId: Int
    ): String {

        val folder = File(
            context.filesDir,
            FOLDER_NAME
        )

        if (!folder.exists()) {
            folder.mkdirs()
        }

        val file = File(
            folder,
            "fox_$foxId.png"
        )

        file.outputStream().use { outputStream ->

            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                outputStream
            )
        }

        return file.absolutePath
    }

    fun load(
        path: String
    ): Bitmap? {

        val file = File(path)

        if (!file.exists()) {
            return null
        }

        return android.graphics.BitmapFactory
            .decodeFile(file.absolutePath)
    }

    fun delete(
        path: String
    ) {

        val file = File(path)

        if (file.exists()) {
            file.delete()
        }
    }
}