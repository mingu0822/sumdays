package com.example.sumdays.auth

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.example.sumdays.MyApplication
import com.jakewharton.threetenabp.AndroidThreeTen

/** Real Android storage/keystore, without seeding diaries or scheduling network workers. */
class InventoryTestApplication : MyApplication() {
    override fun onCreate() {
        check(packageName.endsWith(".inventorycheck"))
        SessionManager.init(this)
        AndroidThreeTen.init(this)
    }
}

class InventoryTestRunner : AndroidJUnitRunner() {
    override fun newApplication(loader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(loader, InventoryTestApplication::class.java.name, context)
}
