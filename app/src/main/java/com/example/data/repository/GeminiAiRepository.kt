package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.ChatMessageRecord
import com.example.data.model.PromptOptimizationResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val TAG = "GeminiAiRepository"

sealed interface ChatStreamEvent {
    data class Partial(val accumulatedText: String) : ChatStreamEvent
    data class Completed(
        val fullText: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val latencyMs: Long
    ) : ChatStreamEvent
}

data class ImageGenResult(
    val bitmap: Bitmap,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val latencyMs: Long
)

class GeminiAiRepository {

    private suspend fun ensureAppCheckTokenReady() {
        try {
            withTimeout(25_000L) {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "App Check token fetch timed out after 25s", e)
            throw IllegalStateException(
                "دریافت تاییدیه امنیتی App Check (reCAPTCHA) زمان‌بر شد. لطفاً اتصال اینترنت یا VPN را بررسی کرده و دوباره تلاش کنید."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "App Check token fetch failed", e)
            throw IllegalStateException(
                "خطا در تاییدیه امنیتی App Check: ${e.localizedMessage ?: "نامشخص"}"
            )
        }
    }

    fun streamChatReply(
        modelName: String,
        systemPrompt: String,
        temperature: Float,
        history: List<ChatMessageRecord>,
        userPrompt: String,
        attachedBitmap: Bitmap?
    ): Flow<ChatStreamEvent> = flow {
        ensureAppCheckTokenReady()
        val startTime = System.currentTimeMillis()
        val config = generationConfig {
            this.temperature = temperature.coerceIn(0f, 2f)
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = config,
            systemInstruction = content { text(systemPrompt) }
        )

        val chatHistory = history.takeLast(20).map { msg ->
            val role = if (msg.role == "model") "model" else "user"
            content(role) { text(msg.text) }
        }

        val chat = model.startChat(history = chatHistory)
        val outgoing = content("user") {
            if (attachedBitmap != null) {
                image(scaleBitmapForPayload(attachedBitmap, 1280))
            }
            text(userPrompt)
        }

        val accumulated = StringBuilder()
        var lastPromptTokens = 0
        var lastCompletionTokens = 0
        var lastTotalTokens = 0

        withTimeout(60_000L) {
            chat.sendMessageStream(outgoing).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(ChatStreamEvent.Partial(accumulated.toString()))
                }
                chunk.usageMetadata?.let { usage ->
                    lastPromptTokens = usage.promptTokenCount
                    lastCompletionTokens = usage.candidatesTokenCount ?: 0
                    lastTotalTokens = usage.totalTokenCount
                }
            }
        }

        val finalText = accumulated.toString().trim()
        if (finalText.isEmpty()) {
            throw IllegalStateException("مدل پاسخی تولید نکرد. لطفاً دوباره تلاش کنید.")
        }
        val latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        emit(
            ChatStreamEvent.Completed(
                fullText = finalText,
                promptTokens = lastPromptTokens,
                completionTokens = lastCompletionTokens,
                totalTokens = lastTotalTokens,
                latencyMs = latencyMs
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun generateOrEditImage(
        modelName: String,
        prompt: String,
        aspectRatio: String,
        sourceBitmap: Bitmap?
    ): ImageGenResult = withContext(Dispatchers.IO) {
        ensureAppCheckTokenReady()
        val startTime = System.currentTimeMillis()
        val mappedRatio = when (aspectRatio) {
            "16:9" -> AspectRatio.LANDSCAPE_16x9
            "9:16" -> AspectRatio.PORTRAIT_9x16
            "4:3" -> AspectRatio.LANDSCAPE_4x3
            "3:4" -> AspectRatio.PORTRAIT_3x4
            else -> AspectRatio.SQUARE_1x1
        }

        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(mappedRatio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }

        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = config
        )

        val response = withTimeout(60_000L) {
            if (sourceBitmap != null) {
                model.generateContent(
                    content {
                        image(scaleBitmapForPayload(sourceBitmap, 1024))
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        val bitmap = imagePart?.image
            ?: throw IllegalStateException("تصویری توسط مدل بازگردانده نشد. پرامپت دیگری را امتحان کنید.")

        val usage = response.usageMetadata
        val latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

        ImageGenResult(
            bitmap = bitmap,
            promptTokens = usage?.promptTokenCount ?: 0,
            completionTokens = usage?.candidatesTokenCount ?: 0,
            totalTokens = usage?.totalTokenCount ?: 0,
            latencyMs = latencyMs
        )
    }

    suspend fun optimizePrompt(rawPrompt: String, targetType: String): PromptOptimizationResult =
        withContext(Dispatchers.IO) {
            ensureAppCheckTokenReady()
            val startTime = System.currentTimeMillis()
            val schema = Schema.obj(
                properties = mapOf(
                    "improvedPrompt" to Schema.string(
                        description = "A clear, richly detailed, production-ready prompt in the same language as the user's input (or English if optimal for image generation)."
                    ),
                    "rationale" to Schema.string(
                        description = "Brief explanation in Persian of what improvements were made."
                    )
                )
            )
            val config = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                temperature = 0.6f
            }
            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = config
            )

            val instruction = if (targetType == "image") {
                "Refine this AI image generation prompt to include clear subject, lighting, composition, camera/render style, and mood. Input: $rawPrompt"
            } else {
                "Refine this AI prompt using prompt engineering best practices (role, context, constraints, expected output format). Keep Persian if input is Persian. Input: $rawPrompt"
            }

            val response = withTimeout(45_000L) {
                model.generateContent(instruction)
            }
            val jsonStr = response.text ?: throw IllegalStateException("پاسخ بهینه‌سازی خالی بود.")
            val json = JSONObject(jsonStr)
            val usage = response.usageMetadata
            val latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

            PromptOptimizationResult(
                improvedPrompt = json.getString("improvedPrompt"),
                rationale = json.optString("rationale", "ساختار و جزئیات پرامپت ارتقا یافت."),
                promptTokens = usage?.promptTokenCount ?: 0,
                completionTokens = usage?.candidatesTokenCount ?: 0,
                totalTokens = usage?.totalTokenCount ?: 0,
                latencyMs = latencyMs
            )
        }

    suspend fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = BitmapFactory.decodeStream(stream)
                raw?.let { scaleBitmapForPayload(it, 1280) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image uri", e)
            null
        }
    }

    suspend fun saveBitmapLocally(context: Context, fileName: String, bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "generated_images")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                fileName
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save bitmap locally", e)
                null
            }
        }

    fun loadLocalBitmap(context: Context, fileName: String?): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        return try {
            val file = File(File(context.filesDir, "generated_images"), fileName)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun exportBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val safeName = "NavaAI_${System.currentTimeMillis()}.png"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
                    put(MediaStore.Images.Media.TITLE, title.take(40))
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/NavaAI"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(IllegalStateException("خطا در دسترسی به گالری"))

                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                Result.success(safeName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export image to gallery", e)
                Result.failure(e)
            }
        }

    suspend fun createShareImageIntent(context: Context, bitmap: Bitmap, caption: String): Intent? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "shared_images")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "share_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare share intent", e)
                null
            }
        }

    private fun scaleBitmapForPayload(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
