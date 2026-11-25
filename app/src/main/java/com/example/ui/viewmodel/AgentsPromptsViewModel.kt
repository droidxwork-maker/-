package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AiChatModel
import com.example.data.model.CustomAssistant
import com.example.data.model.DailyQuotaExceededException
import com.example.data.model.PromptOptimizationResult
import com.example.data.model.SavedPrompt
import com.example.data.model.UiState
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentsPromptsViewModel(
    private val firestoreRepo: FirestoreRepository,
    private val geminiRepo: GeminiAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _noticeMessage = MutableStateFlow<String?>(null)
    val noticeMessage: StateFlow<String?> = _noticeMessage.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _optimizedResult = MutableStateFlow<PromptOptimizationResult?>(null)
    val optimizedResult: StateFlow<PromptOptimizationResult?> = _optimizedResult.asStateFlow()

    val assistantsState: StateFlow<UiState<List<CustomAssistant>>> =
        firestoreRepo.observeAssistants(currentUserId)
            .map<List<CustomAssistant>, UiState<List<CustomAssistant>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing assistants", root)
                emit(UiState.Error(root.localizedMessage ?: "خطا در بارگذاری دستیارها"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val savedPromptsState: StateFlow<UiState<List<SavedPrompt>>> =
        firestoreRepo.observeSavedPrompts(currentUserId)
            .map<List<SavedPrompt>, UiState<List<SavedPrompt>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing prompts", root)
                emit(UiState.Error(root.localizedMessage ?: "خطا در بارگذاری پرامپت‌ها"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    fun saveAssistant(
        existingId: String?,
        name: String,
        roleDescription: String,
        systemPrompt: String,
        modelName: String,
        temperature: Double
    ) {
        viewModelScope.launch {
            firestoreRepo.saveAssistant(
                existingId = existingId,
                name = name,
                roleDescription = roleDescription,
                systemPrompt = systemPrompt,
                modelName = AiChatModel.fromId(modelName).modelId,
                temperature = temperature
            ).onSuccess {
                _noticeMessage.value = "دستیار هوشمند ذخیره و در حساب ابری همگام شد."
            }.onFailure { e ->
                _noticeMessage.value = e.localizedMessage ?: "ذخیره دستیار ناموفق بود."
            }
        }
    }

    fun deleteAssistant(assistantId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteAssistant(assistantId)
                .onFailure { e ->
                    _noticeMessage.value = e.localizedMessage ?: "حذف دستیار ناموفق بود."
                }
        }
    }

    fun savePromptTemplate(
        existingId: String?,
        title: String,
        category: String,
        content: String
    ) {
        viewModelScope.launch {
            firestoreRepo.savePromptTemplate(existingId, title, category, content)
                .onSuccess {
                    _noticeMessage.value = "پرامپت در کتابخانه ذخیره شد."
                }
                .onFailure { e ->
                    _noticeMessage.value = e.localizedMessage ?: "ذخیره پرامپت ناموفق بود."
                }
        }
    }

    fun deleteSavedPrompt(promptId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteSavedPrompt(promptId)
                .onFailure { e ->
                    _noticeMessage.value = e.localizedMessage ?: "حذف پرامپت ناموفق بود."
                }
        }
    }

    fun optimizePrompt(rawPrompt: String, targetType: String) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) {
            _noticeMessage.value = "متن پرامپت اولیه را وارد کنید."
            return
        }
        if (_isOptimizing.value) return
        viewModelScope.launch {
            _isOptimizing.value = true
            var reservedQuotaDate: String? = null
            var produced = false
            try {
                val quota = firestoreRepo.consumeDailyQuotaIfAvailable().getOrThrow()
                reservedQuotaDate = quota.quotaDateUtc

                val res = geminiRepo.optimizePrompt(trimmed, targetType)
                produced = true
                _optimizedResult.value = res
                firestoreRepo.recordOptimizerUsage(
                    modelName = AiChatModel.FLASH_LITE_3_5.modelId,
                    promptTokens = res.promptTokens.toLong(),
                    completionTokens = res.completionTokens.toLong(),
                    totalTokens = res.totalTokens.toLong(),
                    latencyMs = res.latencyMs
                )
            } catch (e: DailyQuotaExceededException) {
                _noticeMessage.value = e.message ?: "سهمیه روزانه شما به پایان رسیده است."
            } catch (e: CancellationException) {
                if (!produced && reservedQuotaDate != null) {
                    withContext(NonCancellable) {
                        firestoreRepo.refundDailyQuotaUnit(reservedQuotaDate)
                    }
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Optimizer failed", e)
                if (!produced && reservedQuotaDate != null) {
                    withContext(NonCancellable) {
                        firestoreRepo.refundDailyQuotaUnit(reservedQuotaDate)
                    }
                }
                _noticeMessage.value = e.localizedMessage ?: "خطا در بهینه‌سازی پرامپت."
            } finally {
                _isOptimizing.value = false
            }
        }
    }

    fun clearOptimizedResult() {
        _optimizedResult.value = null
    }

    fun clearNotice() {
        _noticeMessage.value = null
    }

    companion object {
        private const val TAG = "AgentsPromptsVM"

        fun provideFactory(
            firestoreRepo: FirestoreRepository,
            geminiRepo: GeminiAiRepository,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AgentsPromptsViewModel(firestoreRepo, geminiRepo, currentUserId) }
        }
    }
}
