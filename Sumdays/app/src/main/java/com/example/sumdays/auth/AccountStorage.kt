package com.example.sumdays.auth

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/** Local inventory belongs to the signed-in user; guest data has its own namespace. */
object AccountStorage {
    fun accountKey(): String {
        val userId = SessionManager.getUserId()
        return if (userId >= 0) "user_$userId" else "guest"
    }

    fun preferences(context: Context, name: String): SharedPreferences =
        context.getSharedPreferences("${name}__${accountKey()}", Context.MODE_PRIVATE)

    fun directory(context: Context, name: String): File =
        context.getDir("${name}__${accountKey()}", Context.MODE_PRIVATE)

    /** Resolve against the actual private directory, including symlinks and '..'. */
    fun ownedFile(context: Context, directoryName: String, path: String?): File? {
        if (path.isNullOrBlank()) return null
        return try {
            val file = File(path).canonicalFile
            file.takeIf { it.parentFile == directory(context, directoryName).canonicalFile }
        } catch (_: java.io.IOException) {
            null
        }
    }
}
