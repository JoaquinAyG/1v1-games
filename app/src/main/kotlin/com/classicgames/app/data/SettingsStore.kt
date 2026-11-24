package com.classicgames.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** The player's saved profile. Empty strings mean "not set yet". */
data class Settings(
    val name: String,
    val server: String,
    /** True when this phone runs the game server itself (friends connect to this phone). */
    val hostOnPhone: Boolean = false,
) {
    /** The address this app connects to: this phone's own server, or the saved PC/phone address. */
    val serverToUse: String get() = if (hostOnPhone) "127.0.0.1:8080" else server
}

/** The room the player is in, saved so the app can rejoin the same seat after being closed or killed. */
data class SavedSession(val server: String, val name: String, val room: String, val token: String)

/** Persists the player name, the server choice and the current room seat between app launches. */
class SettingsStore(private val context: Context) {
    private val nameKey = stringPreferencesKey("name")
    private val serverKey = stringPreferencesKey("server")
    private val hostOnPhoneKey = booleanPreferencesKey("hostOnPhone")

    private val sessionServerKey = stringPreferencesKey("session_server")
    private val sessionNameKey = stringPreferencesKey("session_name")
    private val sessionRoomKey = stringPreferencesKey("session_room")
    private val sessionTokenKey = stringPreferencesKey("session_token")

    val settings: Flow<Settings> = context.dataStore.data.map {
        Settings(
            name = it[nameKey].orEmpty(),
            server = it[serverKey].orEmpty(),
            hostOnPhone = it[hostOnPhoneKey] ?: false,
        )
    }

    suspend fun save(name: String, server: String) {
        context.dataStore.edit {
            it[nameKey] = name
            it[serverKey] = server
        }
    }

    suspend fun setHostOnPhone(enabled: Boolean) {
        context.dataStore.edit { it[hostOnPhoneKey] = enabled }
    }

    /** The saved room seat, or null if there is none. */
    val savedSession: Flow<SavedSession?> = context.dataStore.data.map {
        val room = it[sessionRoomKey]
        val token = it[sessionTokenKey]
        if (room.isNullOrEmpty() || token.isNullOrEmpty()) {
            null
        } else {
            SavedSession(
                server = it[sessionServerKey].orEmpty(),
                name = it[sessionNameKey].orEmpty(),
                room = room,
                token = token,
            )
        }
    }

    suspend fun saveSession(session: SavedSession) {
        context.dataStore.edit {
            it[sessionServerKey] = session.server
            it[sessionNameKey] = session.name
            it[sessionRoomKey] = session.room
            it[sessionTokenKey] = session.token
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(sessionServerKey)
            it.remove(sessionNameKey)
            it.remove(sessionRoomKey)
            it.remove(sessionTokenKey)
        }
    }
}
