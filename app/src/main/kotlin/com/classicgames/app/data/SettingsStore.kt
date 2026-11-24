package com.classicgames.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** The player's saved profile. Empty strings mean "not set yet". */
data class Settings(val name: String, val server: String)

/** Persists the player name and server address between app launches. */
class SettingsStore(private val context: Context) {
    private val nameKey = stringPreferencesKey("name")
    private val serverKey = stringPreferencesKey("server")

    val settings: Flow<Settings> = context.dataStore.data.map {
        Settings(name = it[nameKey].orEmpty(), server = it[serverKey].orEmpty())
    }

    suspend fun save(name: String, server: String) {
        context.dataStore.edit {
            it[nameKey] = name
            it[serverKey] = server
        }
    }
}
