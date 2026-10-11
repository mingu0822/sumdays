package com.example.sumdays.auth

import android.content.Context
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.Mockito.withSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],
    application = Application::class)
class SessionManagerTest {

    private lateinit var context: Context
    private lateinit var mockKeyStore: KeyStore

    private val PREFS_NAME = "sumdays_keystore_prefs"
    private val KEY_ALIAS = "SumdaysKeyAlias"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockKeyStore = Mockito.mock(
            KeyStore::class.java,
            withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS)
        )

        val fakeSecretKey = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        Mockito.`when`(mockKeyStore.containsAlias(KEY_ALIAS)).thenReturn(true)
        Mockito.`when`(mockKeyStore.getKey(KEY_ALIAS, null)).thenReturn(fakeSecretKey)

        val ksField = SessionManager::class.java.getDeclaredField("keyStore")
        ksField.isAccessible = true
        ksField.set(SessionManager, mockKeyStore)

        val prefsField = SessionManager::class.java.getDeclaredField("sharedPreferences")
        prefsField.isAccessible = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        prefsField.set(SessionManager, prefs)
        SessionManager::class.java.getDeclaredField("applicationContext").apply {
            isAccessible = true
            set(SessionManager, context)
        }
    }

    @Test
    fun save_and_load_token_roundtrip() {
        assertTrue(SessionManager.saveSession(42, "secret_abc"))
        assertEquals("secret_abc", SessionManager.getToken())
        assertEquals(42, SessionManager.getUserId())
    }

    @Test
    fun clearSession_clears_all_values() {
        SessionManager.saveSession(10, "tok123")
        SessionManager.clearSession()
        assertNull(SessionManager.getToken())
        assertEquals(-1, SessionManager.getUserId())
        assertFalse(SessionManager.isLoggedIn())
    }

    @Test
    fun isLoggedIn_reflects_saved_state() {
        assertFalse(SessionManager.isLoggedIn())
        SessionManager.saveSession(77, "tokx")
        assertTrue(SessionManager.isLoggedIn())
    }

    @Test
    fun corrupt_sharedprefs_returns_null_token() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("encrypted_auth_token", "BADBASE64").apply()
        prefs.edit().putString("encryption_iv", "BADIV").apply()
        assertNull(SessionManager.getToken())
    }

    @Test
    fun failed_session_save_preserves_previous_account_and_inventory() {
        assertTrue(SessionManager.saveSession(10, "first-token"))
        com.example.sumdays.shop.PointPrefs.savePoint(context, 1234)
        Mockito.`when`(mockKeyStore.getKey(KEY_ALIAS, null))
            .thenThrow(java.security.UnrecoverableKeyException("test failure"))

        assertFalse(SessionManager.saveSession(20, "next-token"))
        assertEquals(10, SessionManager.getUserId())
        assertEquals(1234, com.example.sumdays.shop.PointPrefs.getPoint(context))
        assertFalse(SessionManager.saveSession(-1, "invalid-user"))
        assertFalse(SessionManager.saveSession(20, ""))
    }
}
