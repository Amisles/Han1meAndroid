package app.amisles.hanime.data.preferences

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPreferencesTest {

    private val hot = FakePrefs()
    private val secureStore = FakePrefs()
    private val session = SecureSession()
    private val prefs = SplitPreferences(hot, session, setOf("login_cookie", "already_login"))

    @Test
    fun `hot keys never touch the session store`() {
        prefs.edit().putString("theme_mode", "DARK").commit()

        assertFalse(session.isReady())
        assertTrue(secureStore.values.isEmpty())
        assertEquals("DARK", hot.values["theme_mode"])
        assertEquals("DARK", prefs.getString("theme_mode", null))
    }

    @Test
    fun `session reads fall back to defaults until the store opens`() {
        assertEquals("", prefs.getString("login_cookie", ""))
        assertFalse(prefs.contains("login_cookie"))
    }

    @Test
    fun `writes before the store opens are replayed in order`() {
        prefs.edit().putBoolean("already_login", true).commit()
        prefs.edit().putString("login_cookie", "laravel=x").commit()

        assertTrue(session.written)
        assertTrue(secureStore.values.isEmpty())

        session.attach(secureStore)

        assertEquals(true, secureStore.values["already_login"])
        assertEquals("laravel=x", secureStore.values["login_cookie"])
        assertEquals("laravel=x", prefs.getString("login_cookie", ""))
    }

    @Test
    fun `session values become visible once attached`() {
        secureStore.values["login_cookie"] = "laravel=y"
        session.attach(secureStore)

        assertEquals("laravel=y", prefs.getString("login_cookie", ""))
        assertTrue(prefs.contains("login_cookie"))
        assertTrue(hot.values.isEmpty())
    }

    @Test
    fun `failed store never persists session writes`() {
        session.attach(null)

        prefs.edit().putString("login_cookie", "laravel=z").commit()

        assertTrue(session.hasFailed())
        assertTrue(hot.values.isEmpty())
        assertTrue(secureStore.values.isEmpty())
    }

    @Test
    fun `remove reaches the owning store`() {
        secureStore.values["login_cookie"] = "laravel=y"
        hot.values["theme_mode"] = "DARK"
        session.attach(secureStore)

        prefs.edit().remove("login_cookie").remove("theme_mode").commit()

        assertNull(secureStore.values["login_cookie"])
        assertFalse(hot.values.containsKey("theme_mode"))
        assertNull(prefs.getString("login_cookie", null))
    }

    @Test
    fun `clear empties both stores`() {
        secureStore.values["login_cookie"] = "laravel=y"
        hot.values["theme_mode"] = "DARK"
        session.attach(secureStore)

        prefs.edit().clear().commit()

        assertTrue(secureStore.values.isEmpty())
        assertTrue(hot.values.isEmpty())
    }
}

private class FakePrefs : SharedPreferences {
    val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(this)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
}

private class FakeEditor(private val prefs: FakePrefs) : SharedPreferences.Editor {
    private val staged = mutableMapOf<String, Any?>()
    private val removed = mutableListOf<String>()

    private fun stage(key: String, value: Any?): SharedPreferences.Editor {
        staged[key] = value
        return this
    }

    override fun putString(key: String, value: String?): SharedPreferences.Editor = stage(key, value)

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
        stage(key, values?.toSet())

    override fun putInt(key: String, value: Int): SharedPreferences.Editor = stage(key, value)

    override fun putLong(key: String, value: Long): SharedPreferences.Editor = stage(key, value)

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor = stage(key, value)

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = stage(key, value)

    override fun remove(key: String): SharedPreferences.Editor {
        removed += key
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        removed += prefs.values.keys
        return this
    }

    override fun commit(): Boolean {
        prefs.values.putAll(staged)
        removed.forEach { prefs.values.remove(it) }
        return true
    }

    override fun apply() {
        commit()
    }
}
