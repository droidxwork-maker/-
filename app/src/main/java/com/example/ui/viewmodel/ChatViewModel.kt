package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AiChatModel
import com.example.data.model.AiPersona
import com.example.data.model.AiPersonas
import com.example.data.model.ChatMessageRecord
import com.example.data.model.Conversation
import com.example.data.model.CustomAssistant
import com.example.data.model.UiState
import com.example.data.repository.ChatStreamEvent
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val firestoreRepo: FirestoreRepository,
    private val geminiRepo: GeminiAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    private val _selectedModel = MutableStateFlow(AiChatModel.FLASH_3_8)
    val selectedModel: StateFlow<AiChatModel> = _selectedModel.asStateFlow()

    private val _selectedPersona = MutableStateFlow(AiPersonas.all.first())
    val selectedPersona: StateFlow<AiPersona> = _selectedPersona.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _systemPromptOverride = MutableStateFlow("")
    val systemPromptOverride: StateFlow<String> = _systemPromptOverride.asStateFlow()

    private val _prefillDraft = MutableStateFlow<String?>(null)
    val prefillDraft: StateFlow<String?> = _prefillDraft.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _attachedBitmap = MutableStateFlow<Bitmap?>(null)
    val attachedBitmap: StateFlow<Bitmap?> = _attachedBitmap.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var activeStreamJob: Job? = null

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        firestoreRepo.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing conversations", root)
                emit(UiState.Error(root.localizedMessage ?: "خطا در بارگذاری گفتگوها"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val messagesState: StateFlow<UiState<List<ChatMessageRecord>>> =
        _selectedConversationId.flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf<UiState<List<ChatMessageRecord>>>(UiState.Success(emptyList()))
            } else {
                firestoreRepo.observeMessages(currentUserId, convId)
                    .map<List<ChatMessageRecord>, UiState<List<ChatMessageRecord>>> {
                        UiState.Success(it)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Error observing messages", root)
                        emit(UiState.Error(root.localizedMessage ?: "خطا در بارگذاری پیام‌ها"))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Success(emptyList())
        )

    fun selectConversation(conversation: Conversation) {
        activeStreamJob?.cancel()
        _selectedConversationId.value = conversation.id
        _selectedModel.value = AiChatModel.fromId(conversation.modelName)
        _selectedPersona.value = AiPersonas.byId(conversation.personaId)
        _temperature.value = conversation.temperature.toFloat().coerceIn(0f, 2f)
        _systemPromptOverride.value = conversation.systemPromptOverride.orEmpty()
        _streamingReply.value = null
        _isSending.value = false
        _errorMessage.value = null
    }

    fun startNewChat(persona: AiPersona? = null, initialPrompt: String? = null) {
        activeStreamJob?.cancel()
        _selectedConversationId.value = null
        val chosen = persona ?: AiPersonas.all.first()
        _selectedPersona.value = chosen
        _selectedModel.value = chosen.defaultModel
        _temperature.value = chosen.defaultTemperature.toFloat()
        _systemPromptOverride.value = if (chosen.isCustom) chosen.systemPrompt else ""
        _prefillDraft.value = initialPrompt
        _streamingReply.value = null
        _isSending.value = false
        _errorMessage.value = null
    }

    fun startChatWithCustomAssistant(assistant: CustomAssistant) {
        startNewChat(persona = assistant.toPersona())
        viewModelScope.launch {
            firestoreRepo.incrementAssistantUsage(assistant.id)
        }
    }

    fun consumePrefillDraft() {
        _prefillDraft.value = null
    }

    fun selectModel(model: AiChatModel) {
        _selectedModel.value = model
    }

    fun selectPersona(persona: AiPersona) {
        _selectedPersona.value = persona
        _selectedModel.value = persona.defaultModel
        _temperature.value = persona.defaultTemperature.toFloat()
        _systemPromptOverride.value = if (persona.isCustom) persona.systemPrompt else ""
    }

    fun updateParameters(temperature: Float, systemOverride: String) {
        val clamped = temperature.coerceIn(0f, 1.5f)
        _temperature.value = clamped
        _systemPromptOverride.value = systemOverride
        val convId = _selectedConversationId.value
        if (!convId.isNullOrBlank()) {
            viewModelScope.launch {
                firestoreRepo.updateConversationParams(convId, clamped.toDouble(), systemOverride)
            }
        }
    }

    fun attachImageFromUri(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val bitmap = geminiRepo.decodeUriToBitmap(appContext, uri)
            if (bitmap != null) {
                _attachedBitmap.value = bitmap
            } else {
                _errorMessage.value = "خواندن تصویر انتخاب‌شده ممکن نشد."
            }
        }
    }

    fun clearAttachment() {
        _attachedBitmap.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            firestoreRepo.renameConversation(conversationId, newTitle)
                .onFailure { e -> _errorMessage.value = e.localizedMessage ?: "تغییر نام ناموفق بود." }
        }
    }

    fun togglePinConversation(conversation: Conversation) {
        viewModelScope.launch {
            firestoreRepo.toggleConversationPin(conversation.id, !conversation.pinned)
                .onFailure { e -> _errorMessage.value = e.localizedMessage ?: "تغییر وضعیت سنجاق ناموفق بود." }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteConversation(conversationId)
                .onSuccess {
                    if (_selectedConversationId.value == conversationId) {
                        _selectedConversationId.value = null
                    }
                }
                .onFailure { e ->
                    _errorMessage.value = e.localizedMessage ?: "حذف گفتگو ناموفق بود."
                }
        }
    }

    fun stopStreaming() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        _streamingReply.value = null
        _isSending.value = false
    }

    fun sendMessage(rawPrompt: String) {
        val promptText = rawPrompt.trim()
        val bitmapToSend = _attachedBitmap.value
        if (promptText.isEmpty() && bitmapToSend == null) return
        if (_isSending.value) return

        val effectivePrompt = if (promptText.isNotEmpty()) promptText else "لطفاً این تصویر را به دقت تحلیل کن."
        val activeModel = _selectedModel.value
        val activePersona = _selectedPersona.value
        val effectiveTemp = _temperature.value
        val effectiveSystem = _systemPromptOverride.value.trim().ifEmpty { activePersona.systemPrompt }
        val currentHistory = (messagesState.value as? UiState.Success)?.data ?: emptyList()

        activeStreamJob = viewModelScope.launch {
            _isSending.value = true
            _errorMessage.value = null
            _attachedBitmap.value = null
            _streamingReply.value = ""

            try {
                var convId = _selectedConversationId.value
                val isFirstMessage = convId == null

                if (convId == null) {
                    val created = firestoreRepo.createConversation(
                        title = effectivePrompt.take(48),
                        modelName = activeModel.modelId,
                        personaId = activePersona.id,
                        systemPromptOverride = _systemPromptOverride.value.trim().takeIf { it.isNotEmpty() },
                        temperature = effectiveTemp.toDouble()
                    )
                    convId = created.getOrElse { err -> throw err }
                    _selectedConversationId.value = convId
                }

                firestoreRepo.addMessageAndUpdateConversation(
                    conversationId = convId,
                    role = "user",
                    text = effectivePrompt,
                    modelName = activeModel.modelId,
                    hasImageAttachment = bitmapToSend != null,
                    newTitleIfFirst = if (isFirstMessage) effectivePrompt.take(48) else null
                ).getOrThrow()

                streamAndPersistModelReply(
                    convId = convId,
                    model = activeModel,
                    systemPrompt = effectiveSystem,
                    temperature = effectiveTemp,
                    history = currentHistory,
                    userPrompt = effectivePrompt,
                    attachedBitmap = bitmapToSend
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat streaming timed out", e)
                _errorMessage.value = "زمان پاسخ‌گویی مدل به پایان رسید (۶۰ ثانیه). لطفاً دوباره تلاش کنید."
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Chat quota exceeded", e)
                _errorMessage.value = "سقف مجاز استفاده از مدل هوش مصنوعی پر شده است."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat message failed", e)
                _errorMessage.value = e.localizedMessage ?: "ارسال پیام به هوش مصنوعی با خطا مواجه شد."
            } finally {
                _streamingReply.value = null
                _isSending.value = false
                activeStreamJob = null
            }
        }
    }

    fun regenerateLastResponse() {
        if (_isSending.value) return
        val convId = _selectedConversationId.value ?: return
        val messages = (messagesState.value as? UiState.Success)?.data ?: return
        val lastUserIdx = messages.indexOfLast { it.role == "user" }
        if (lastUserIdx == -1) return

        val lastUserMsg = messages[lastUserIdx]
        val lastModelMsg = messages.drop(lastUserIdx + 1).lastOrNull { it.role == "model" }
        val historyBeforeUser = messages.take(lastUserIdx)

        val activeModel = _selectedModel.value
        val effectiveTemp = _temperature.value
        val effectiveSystem = _systemPromptOverride.value.trim().ifEmpty { _selectedPersona.value.systemPrompt }

        activeStreamJob = viewModelScope.launch {
            _isSending.value = true
            _errorMessage.value = null
            _streamingReply.value = ""
            try {
                if (lastModelMsg != null) {
                    firestoreRepo.deleteMessage(convId, lastModelMsg.id)
                }
                streamAndPersistModelReply(
                    convId = convId,
                    model = activeModel,
                    systemPrompt = effectiveSystem,
                    temperature = effectiveTemp,
                    history = historyBeforeUser,
                    userPrompt = lastUserMsg.text,
                    attachedBitmap = null
                )
            } catch (e: TimeoutCancellationException) {
                _errorMessage.value = "زمان تولید مجدد پاسخ به پایان رسید."
            } catch (e: QuotaExceededException) {
                _errorMessage.value = "سقف مجاز استفاده از مدل پر شده است."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "تولید مجدد پاسخ ناموفق بود."
            } finally {
                _streamingReply.value = null
                _isSending.value = false
                activeStreamJob = null
            }
        }
    }

    private suspend fun streamAndPersistModelReply(
        convId: String,
        model: AiChatModel,
        systemPrompt: String,
        temperature: Float,
        history: List<ChatMessageRecord>,
        userPrompt: String,
        attachedBitmap: Bitmap?
    ) {
        var completedEvent: ChatStreamEvent.Completed? = null
        geminiRepo.streamChatReply(
            modelName = model.modelId,
            systemPrompt = systemPrompt,
            temperature = temperature,
            history = history,
            userPrompt = userPrompt,
            attachedBitmap = attachedBitmap
        ).collect { event ->
            when (event) {
                is ChatStreamEvent.Partial -> _streamingReply.value = event.accumulatedText
                is ChatStreamEvent.Completed -> completedEvent = event
            }
        }

        val done = completedEvent
        if (done != null && done.fullText.isNotEmpty()) {
            firestoreRepo.addMessageAndUpdateConversation(
                conversationId = convId,
                role = "model",
                text = done.fullText,
                modelName = model.modelId,
                hasImageAttachment = false,
                promptTokens = done.promptTokens.toLong(),
                completionTokens = done.completionTokens.toLong(),
                totalTokens = done.totalTokens.toLong(),
                latencyMs = done.latencyMs
            ).getOrThrow()
        }
    }

    fun buildMarkdownExport(title: String, messages: List<ChatMessageRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("# $title")
        sb.appendLine()
        for (msg in messages) {
            val speaker = if (msg.role == "user") "کاربر" else "نوا هوش (${msg.modelName ?: "Gemini"})"
            sb.appendLine("### $speaker")
            sb.appendLine(msg.text)
            if (msg.role == "model" && msg.totalTokens > 0L) {
                sb.appendLine("> Tokens: ${msg.totalTokens} | Latency: ${msg.latencyMs}ms")
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "ChatVM"

        fun provideFactory(
            firestoreRepo: FirestoreRepository,
            geminiRepo: GeminiAiRepository,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(firestoreRepo, geminiRepo, currentUserId) }
        }
    }
}
