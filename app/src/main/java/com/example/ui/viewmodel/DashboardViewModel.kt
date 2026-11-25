package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.Conversation
import com.example.data.model.CustomAssistant
import com.example.data.model.UiState
import com.example.data.model.UsageLog
import com.example.data.model.UserProfile
import com.example.data.repository.FirestoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val firestoreRepo: FirestoreRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        viewModelScope.launch {
            firestoreRepo.ensureUserProfile()
                .onFailure { e ->
                    Log.w(TAG, "Failed to sync user profile", e)
                }
        }
    }

    val profileState: StateFlow<UiState<UserProfile?>> =
        firestoreRepo.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing profile", root)
                emit(UiState.Error(root.localizedMessage ?: "خطا در دریافت پروفایل"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

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
                emit(UiState.Error(root.localizedMessage ?: "خطا در دریافت گفتگوها"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val usageLogsState: StateFlow<UiState<List<UsageLog>>> =
        firestoreRepo.observeUsageLogs(currentUserId)
            .map<List<UsageLog>, UiState<List<UsageLog>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing usage logs", root)
                emit(UiState.Error(root.localizedMessage ?: "خطا در دریافت گزارش مصرف"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

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
                emit(UiState.Error(root.localizedMessage ?: "خطا در دریافت دستیارها"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    fun updatePreferredTemperature(temperature: Double) {
        viewModelScope.launch {
            firestoreRepo.updatePreferredTemperature(temperature)
                .onFailure { e ->
                    _actionError.value = e.localizedMessage ?: "خطا در ذخیره تنظیمات"
                }
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    companion object {
        private const val TAG = "DashboardVM"

        fun provideFactory(
            firestoreRepo: FirestoreRepository,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(firestoreRepo, currentUserId) }
        }
    }
}
