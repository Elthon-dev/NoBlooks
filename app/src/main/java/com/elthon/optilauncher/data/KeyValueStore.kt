package com.elthon.optilauncher.data

import android.content.Context
import android.content.SharedPreferences
import com.elthon.optilauncher.core.settings.KeyValueStore

/**
 * SharedPreferences-backed [KeyValueStore]. Values are written synchronously
 * into the in-memory SharedPreferences cache and flushed asynchronously by the
 * platform, which is plenty for settings/favourites of this size and keeps
 * writes off the critical path of a tap.
 */
class SharedPreferencesStore(
    context: Context,
    fileName: String = FILE_NAME
) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    override fun readString(key: String): String? = prefs.getString(key, null)

    override fun writeString(key: String, value: String?) {
        prefs.edit().let { editor ->
            if (value == null) editor.remove(key) else editor.putString(key, value)
            editor.apply()
        }
    }

    companion object {
        const val FILE_NAME = "noblooks_state"
    }
}
