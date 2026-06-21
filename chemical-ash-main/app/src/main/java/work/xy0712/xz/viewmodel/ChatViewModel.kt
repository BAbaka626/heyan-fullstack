package work.xy0712.xz.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import work.xy0712.xz.data.local.ChatSessionStore
import work.xy0712.xz.data.model.AskHistoryTurn
import work.xy0712.xz.data.model.AskResponse
import work.xy0712.xz.data.model.ChatSession
import work.xy0712.xz.data.model.ChatTurn
import work.xy0712.xz.data.repository.ChemicalRepository

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = ChatSessionStore(application)
    private val repository = ChemicalRepository(work.xy0712.xz.data.api.ApiConfig.BASE_URL)

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String>("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isAsking = MutableStateFlow(false)
    val isAsking: StateFlow<Boolean> = _isAsking.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog.asStateFlow()

    init {
        viewModelScope.launch {
            val storedSessions = sessionStore.sessionsFlow.first()
            val storedCurrentId = sessionStore.currentSessionIdFlow.first()

            if (storedSessions.isEmpty()) {
                createNewSession()
            } else {
                _sessions.value = storedSessions
                _currentSessionId.value = storedCurrentId ?: storedSessions.first().id
            }
        }
    }

    val currentSession: ChatSession?
        get() = _sessions.value.find { it.id == _currentSessionId.value }

    val currentTurns: List<ChatTurn>
        get() = currentSession?.turns ?: emptyList()

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun toggleDrawer() {
        _drawerOpen.value = !_drawerOpen.value
    }

    fun closeDrawer() {
        _drawerOpen.value = false
    }

    fun createNewSession() {
        val newSession = ChatSession(
            id = generateSessionId(),
            title = "对话${_sessions.value.size + 1}"
        )
        _sessions.value = listOf(newSession) + _sessions.value
        _currentSessionId.value = newSession.id
        _inputText.value = ""
        _errorMessage.value = null
        persist()
    }

    fun switchSession(sessionId: String) {
        _currentSessionId.value = sessionId
        _inputText.value = ""
        _errorMessage.value = null
        closeDrawer()
        persist()
    }

    fun deleteSession(sessionId: String) {
        val updated = _sessions.value.filterNot { it.id == sessionId }
        if (updated.isEmpty()) {
            val newSession = ChatSession(id = generateSessionId(), title = "对话1")
            _sessions.value = listOf(newSession)
            _currentSessionId.value = newSession.id
        } else {
            _sessions.value = updated
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = updated.first().id
            }
        }
        persist()
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val cleanTitle = newTitle.trim().takeIf { it.isNotEmpty() } ?: return
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) session.copy(title = cleanTitle) else session
        }
        persist()
    }

    fun showRenameDialog() {
        _showRenameDialog.value = true
    }

    fun dismissRenameDialog() {
        _showRenameDialog.value = false
    }

    fun sendQuestion() {
        val question = _inputText.value.trim()
        if (question.isEmpty() || _isAsking.value) return

        viewModelScope.launch {
            _isAsking.value = true
            _errorMessage.value = null

            try {
                // 1. 提取并上报历史轮次：
                // 注意：在多轮对话中，带给后端的依然是基础 RAG 纯文本和溯源标签，防止历史的图形 JSON 拖慢网络帧率
                val history = currentTurns.takeLast(6).map { turn ->
                    AskHistoryTurn(
                        question = turn.question,
                        answer = turn.response.answer,
                        sources = turn.response.sources
                    )
                }

                // 2. 接收来自 3100 端口的网络响应：
                // 通过底层模型的字段重构，此处的 response 包含了完整的：
                // .answer -> 大白话文本
                // .mode -> "diagram" 图形拦截器标记
                // .type -> "pie_chart" / "knowledge_graph" 图形细分类型
                // .data -> 对应的 ECharts JSON 全量原始节点
                val response = repository.ask(question, history)
                val newTurn = ChatTurn(question = question, response = response)

                val updatedSessions = _sessions.value.map { session ->
                    if (session.id == _currentSessionId.value) {
                        val newTitle = if (session.title.startsWith("对话") && session.turns.isEmpty()) {
                            question.take(16) + if (question.length > 16) "..." else ""
                        } else session.title
                        session.copy(
                            title = newTitle,
                            turns = session.turns + newTurn, // 将带有高级图形属性的新轮次无损塞入会话
                            updatedAt = System.currentTimeMillis()
                        )
                    } else session
                }
                _sessions.value = updatedSessions
                _inputText.value = ""
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "ask failed", e)
                _errorMessage.value = "${e.javaClass.simpleName}: ${e.message ?: "问答请求失败"}"
            } finally {
                _isAsking.value = false
            }
            // 3. 驱动本地持久化：
            // 将包含 mode 和 data 字段的完整聊天记录，实时持久化进本地存储，保证即使冷启动，之前的图表依然能在界面渲染出来。
            persist()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun persist() {
        viewModelScope.launch {
            sessionStore.saveSessions(_sessions.value, _currentSessionId.value)
        }
    }

    private fun generateSessionId(): String =
        "chat-${System.currentTimeMillis()}-${(1000..9999).random()}"
}