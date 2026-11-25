package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AiImageModel
import com.example.data.model.ImageAspectOption
import com.example.data.model.ImageCreation
import com.example.data.model.UiState
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(
        val bitmap: Bitmap,
        val prompt: String,
        val latencyMs: Long,
        val modelName: String
    ) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

class ImageStudioViewModel(
    private val firestoreRepo: FirestoreRepository,
    private val geminiRepo: GeminiAiRepository,
    private val currentUserId: String
) : ViewModel() {

    val stylePresets = listOf(
        "واقع‌گرایانه",
        "سایبرپانک",
        "رندر سه‌بعدی",
        "مینیمال مدرن",
        "آبرنگ هنری",
        "انیمه ژاپنی"
    )

    private val _selectedModel = MutableStateFlow(AiImageModel.NANO_BANANA_2_1)
    val selectedModel: StateFlow<AiImageModel> = _selectedModel.asStateFlow()

    private val _selectedStyle = MutableStateFlow(stylePresets.first())
    val selectedStyle: StateFlow<String> = _selectedStyle.asStateFlow()

    private val _selectedAspect = MutableStateFlow(ImageAspectOption.SQUARE)
    val selectedAspect: StateFlow<ImageAspectOption> = _selectedAspect.asStateFlow()

    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _generationState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val generationState: StateFlow<ImageGenUiState> = _generationState.asStateFlow()

    private val _isOptimizingPrompt = MutableStateFlow(false)
    val isOptimizingPrompt: StateFlow<Boolean> = _isOptimizingPrompt.asStateFlow()

    private val _noticeMessage = MutableStateFlow<String?>(null)
    val noticeMessage: StateFlow<String?> = _noticeMessage.asStateFlow()

    val creationsState: StateFlow<UiState<List<ImageCreation>>> =
        firestoreRepo.observeImageCreations(currentUserId)
            .map<List<ImageCreation>, UiState<List<ImageCreation>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing image creations", root)
                emit(UiState.Error(root.localizedMessage ?: "خطا در دریافت تاریخچه تصاویر"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    fun selectModel(model: AiImageModel) {
        _selectedModel.value = model
    }

    fun selectStyle(style: String) {
        _selectedStyle.value = style
    }

    fun selectAspect(aspect: ImageAspectOption) {
        _selectedAspect.value = aspect
    }

    fun attachReferenceImage(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val bitmap = geminiRepo.decodeUriToBitmap(appContext, uri)
            if (bitmap != null) {
                _sourceBitmap.value = bitmap
            } else {
                _noticeMessage.value = "بارگذاری تصویر مرجع ناموفق بود."
            }
        }
    }

    fun clearReferenceImage() {
        _sourceBitmap.value = null
    }

    fun clearNotice() {
        _noticeMessage.value = null
    }

    fun optimizePrompt(rawPrompt: String, onOptimized: (String) -> Unit) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) {
            _noticeMessage.value = "ابتدا یک ایده اولیه بنویسید تا هوش مصنوعی آن را ارتقا دهد."
            return
        }
        if (_isOptimizingPrompt.value) return
        viewModelScope.launch {
            _isOptimizingPrompt.value = true
            try {
                val res = geminiRepo.optimizePrompt(trimmed, targetType = "image")
                firestoreRepo.recordOptimizerUsage(
                    modelName = "gemini-3.8-flash",
                    promptTokens = res.promptTokens.toLong(),
                    completionTokens = res.completionTokens.toLong(),
                    totalTokens = res.totalTokens.toLong(),
                    latencyMs = res.latencyMs
                )
                onOptimized(res.improvedPrompt)
                _noticeMessage.value = res.rationale
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Optimize image prompt failed", e)
                _noticeMessage.value = e.localizedMessage ?: "بهینه‌سازی پرامپت ناموفق بود."
            } finally {
                _isOptimizingPrompt.value = false
            }
        }
    }

    fun generateImage(context: Context, rawPrompt: String, negativePrompt: String) {
        val trimmed = rawPrompt.trim()
        val trimmedNeg = negativePrompt.trim()
        if (trimmed.isEmpty()) {
            _noticeMessage.value = "لطفاً توصیف تصویر را وارد کنید."
            return
        }
        if (_generationState.value is ImageGenUiState.Loading) return

        val appContext = context.applicationContext
        val model = _selectedModel.value
        val style = _selectedStyle.value
        val aspect = _selectedAspect.value
        val reference = _sourceBitmap.value

        val basePrompt = if (reference != null) {
            "$trimmed (Style: $style)"
        } else {
            "$trimmed, in $style style, high detail, clean composition"
        }
        val fullPrompt = if (trimmedNeg.isNotEmpty()) {
            "$basePrompt. Avoid and exclude: $trimmedNeg"
        } else {
            basePrompt
        }

        viewModelScope.launch {
            _generationState.value = ImageGenUiState.Loading
            try {
                val result = geminiRepo.generateOrEditImage(
                    modelName = model.modelId,
                    prompt = fullPrompt,
                    aspectRatio = aspect.ratioValue,
                    sourceBitmap = reference
                )
                val fileName = "img_${System.currentTimeMillis()}.png"
                val savedName = geminiRepo.saveBitmapLocally(appContext, fileName, result.bitmap)

                firestoreRepo.recordImageCreation(
                    prompt = trimmed,
                    negativePrompt = trimmedNeg.takeIf { it.isNotEmpty() },
                    stylePreset = style,
                    aspectRatio = aspect.ratioValue,
                    modelName = model.modelId,
                    localFileName = savedName,
                    editedFromSource = reference != null,
                    promptTokens = result.promptTokens.toLong(),
                    completionTokens = result.completionTokens.toLong(),
                    totalTokens = result.totalTokens.toLong(),
                    latencyMs = result.latencyMs
                )

                _generationState.value = ImageGenUiState.Success(
                    bitmap = result.bitmap,
                    prompt = trimmed,
                    latencyMs = result.latencyMs,
                    modelName = model.titleFa
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _generationState.value = ImageGenUiState.Error(
                    "ساخت تصویر بیش از ۶۰ ثانیه طول کشید. لطفاً دوباره امتحان کنید."
                )
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image generation quota exceeded", e)
                _generationState.value = ImageGenUiState.Error(
                    "سقف مجاز تولید تصویر به پایان رسیده یا پروژه نیاز به پلن Blaze دارد."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _generationState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "تولید تصویر با خطا مواجه شد."
                )
            }
        }
    }

    fun exportToGallery(context: Context, bitmap: Bitmap, title: String) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            geminiRepo.exportBitmapToGallery(appContext, bitmap, title)
                .onSuccess {
                    _noticeMessage.value = "تصویر با موفقیت در گالری ذخیره شد."
                }
                .onFailure { e ->
                    _noticeMessage.value = e.localizedMessage ?: "ذخیره در گالری ناموفق بود."
                }
        }
    }

    fun shareImage(context: Context, bitmap: Bitmap, title: String) {
        viewModelScope.launch {
            val intent = geminiRepo.createShareImageIntent(context.applicationContext, bitmap, title)
            if (intent != null) {
                val chooser = Intent.createChooser(intent, "اشتراک‌گذاری تصویر")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } else {
                _noticeMessage.value = "آماده‌سازی تصویر برای اشتراک‌گذاری ناموفق بود."
            }
        }
    }

    fun loadCreationPreview(context: Context, creation: ImageCreation) {
        val bitmap = geminiRepo.loadLocalBitmap(context.applicationContext, creation.localFileName)
        if (bitmap != null) {
            _generationState.value = ImageGenUiState.Success(
                bitmap = bitmap,
                prompt = creation.prompt,
                latencyMs = creation.latencyMs,
                modelName = AiImageModel.fromId(creation.modelName).titleFa
            )
        } else {
            _noticeMessage.value = "فایل محلی این تصویر در دستگاه موجود نیست؛ پرامپت برای ساخت مجدد بارگذاری شد."
        }
    }

    fun deleteCreation(creationId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteImageCreation(creationId)
                .onFailure { e ->
                    _noticeMessage.value = e.localizedMessage ?: "حذف مورد ناموفق بود."
                }
        }
    }

    companion object {
        private const val TAG = "ImageStudioVM"

        fun provideFactory(
            firestoreRepo: FirestoreRepository,
            geminiRepo: GeminiAiRepository,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImageStudioViewModel(firestoreRepo, geminiRepo, currentUserId) }
        }
    }
}
