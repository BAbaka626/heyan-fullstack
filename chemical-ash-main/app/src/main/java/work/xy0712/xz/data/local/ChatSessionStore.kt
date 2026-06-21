package work.xy0712.xz.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import work.xy0712.xz.data.model.ChatSession

private val Context.chatDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_sessions")

class ChatSessionStore(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val SESSIONS_KEY = stringPreferencesKey("sessions")
        private val CURRENT_ID_KEY = stringPreferencesKey("current_session_id")
    }

    val sessionsFlow: Flow<List<ChatSession>> = context.chatDataStore.data.map { prefs ->
        val json = prefs[SESSIONS_KEY] ?: "[]"
        val type = object : TypeToken<List<ChatSession>>() {}.type
        gson.fromJson<List<ChatSession>>(json, type) ?: emptyList()
    }

    val currentSessionIdFlow: Flow<String?> = context.chatDataStore.data.map { prefs ->
        prefs[CURRENT_ID_KEY]
    }

    suspend fun saveSessions(sessions: List<ChatSession>, currentId: String) {
        context.chatDataStore.edit { prefs ->
            prefs[SESSIONS_KEY] = gson.toJson(sessions)
            prefs[CURRENT_ID_KEY] = currentId
        }
    }
}
