package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.AiChatModel
import com.example.data.model.ChatMessageRecord
import com.example.data.model.PromptOptimizationResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import kotlin.random.Random

private const val TAG = "GeminiAiRepository"
private const val MAX_RETRY_ATTEMPTS = 3
private const val MIN_REQUEST_INTERVAL_MS = 850L
private const val MAX_HISTORY_MESSAGES = 14
private const val MAX_HISTORY_CHARS = 16_000

sealed interface ChatStreamEvent {
    data class Retrying(val attempt: Int, val maxAttempts: Int, val delayMs: Long) : ChatStreamEvent
    data class Partial(val accumulatedText: String) : ChatStreamEvent
    data class Completed(
        val fullText: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val latencyMs: Long
    ) : ChatStreamEvent
}

class GeminiAiRepository {

    private val requestRateMutex = Mutex()
    private var lastDispatchEpochMs = 0L

    private suspend fun throttleBurstRequests() {
        requestRateMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastDispatchEpochMs
            if (lastDispatchEpochMs > 0L && elapsed < MIN_REQUEST_INTERVAL_MS) {
                delay(MIN_REQUEST_INTERVAL_MS - elapsed)
            }
            lastDispatchEpochMs = System.currentTimeMillis()
        }
    }

    private suspend fun ensureSecuritySessionReady() {
        try {
            withTimeout(25_000L) {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Security attestation timed out after 25s", e)
            throw IllegalStateException(
                "برقراری ارتباط امن با سرور زمان‌بر شد. لطفاً اتصال اینترنت خود را بررسی کرده و دوباره تلاش کنید."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Security attestation failed", e)
            throw IllegalStateException(
                "خطا در تاییدیه امنیتی نشست. لطفاً اتصال اینترنت را بررسی کرده و مجدداً تلاش کنید."
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
        throttleBurstRequests()
        ensureSecuritySessionReady()

        val activeModelId = AiChatModel.fromId(modelName).modelId
        val startTime = System.currentTimeMillis()
        val config = generationConfig {
            this.temperature = temperature.coerceIn(0f, 2f)
        }
        val model = Firebase.ai.generativeModel(
            modelName = activeModelId,
            generationConfig = config,
            systemInstruction = content { text(systemPrompt) }
        )

        val trimmedHistory = compactHistory(history)
        val chatHistory = trimmedHistory.map { msg ->
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

        var attempt = 1
        while (true) {
            val accumulated = StringBuilder()
            var lastPromptTokens = 0
            var lastCompletionTokens = 0
            var lastTotalTokens = 0

            try {
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
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val canRetry = attempt < MAX_RETRY_ATTEMPTS &&
                    accumulated.isEmpty() &&
                    isTransientOverloadError(e)

                if (canRetry) {
                    val backoffMs = computeBackoffWithJitter(attempt)
                    Log.w(TAG, "Transient server load on attempt $attempt; retrying in ${backoffMs}ms", e)
                    emit(ChatStreamEvent.Retrying(attempt = attempt, maxAttempts = MAX_RETRY_ATTEMPTS - 1, delayMs = backoffMs))
                    delay(backoffMs)
                    attempt++
                } else {
                    throw IllegalStateException(mapFriendlyErrorMessage(e), e)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun optimizePrompt(rawPrompt: String, targetType: String): PromptOptimizationResult =
        withContext(Dispatchers.IO) {
            throttleBurstRequests()
            ensureSecuritySessionReady()

            val startTime = System.currentTimeMillis()
            val schema = Schema.obj(
                properties = mapOf(
                    "improvedPrompt" to Schema.string(
                        description = "A clear, richly detailed, production-ready prompt in the same language as the user's input."
                    ),
                    "rationale" to Schema.string(
                        description = "Brief explanation in Persian of what improvements were made."
                    )
                )
            )
            val config = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                temperature = 0.55f
            }
            val model = Firebase.ai.generativeModel(
                modelName = AiChatModel.FLASH_LITE_3_5.modelId,
                generationConfig = config
            )

            val instruction = when (targetType) {
                "code" -> "Refine this software engineering / coding prompt to include architecture constraints, edge cases, language/framework expectations, and clean output structure. Keep Persian if input is Persian. Input: $rawPrompt"
                "content" -> "Refine this writing & marketing prompt to include target audience, tone, structure, key takeaways, and clear call-to-action. Keep Persian if input is Persian. Input: $rawPrompt"
                else -> "Refine this AI prompt using prompt engineering best practices (role, context, constraints, expected output format). Keep Persian if input is Persian. Input: $rawPrompt"
            }

            var attempt = 1
            while (true) {
                try {
                    val response = withTimeout(45_000L) {
                        model.generateContent(instruction)
                    }
                    val jsonStr = response.text ?: throw IllegalStateException("پاسخ بهینه‌سازی خالی بود.")
                    val json = JSONObject(jsonStr)
                    val usage = response.usageMetadata
                    val latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

                    return@withContext PromptOptimizationResult(
                        improvedPrompt = json.getString("improvedPrompt"),
                        rationale = json.optString("rationale", "ساختار و جزئیات پرامپت ارتقا یافت."),
                        promptTokens = usage?.promptTokenCount ?: 0,
                        completionTokens = usage?.candidatesTokenCount ?: 0,
                        totalTokens = usage?.totalTokenCount ?: 0,
                        latencyMs = latencyMs
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (attempt < MAX_RETRY_ATTEMPTS && isTransientOverloadError(e)) {
                        val backoffMs = computeBackoffWithJitter(attempt)
                        Log.w(TAG, "Optimizer transient error on attempt $attempt; retrying in ${backoffMs}ms", e)
                        delay(backoffMs)
                        attempt++
                    } else {
                        throw IllegalStateException(mapFriendlyErrorMessage(e), e)
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
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

    private fun compactHistory(history: List<ChatMessageRecord>): List<ChatMessageRecord> {
        val recent = history.takeLast(MAX_HISTORY_MESSAGES)
        var totalChars = 0
        val result = ArrayList<ChatMessageRecord>(recent.size)
        for (i in recent.indices.reversed()) {
            val item = recent[i]
            val len = item.text.length
            if (totalChars + len > MAX_HISTORY_CHARS && result.isNotEmpty()) break
            result.add(item)
            totalChars += len
        }
        return result.reversed()
    }

    private fun computeBackoffWithJitter(attempt: Int): Long {
        val base = 1400L * (1L shl (attempt - 1))
        val jitter = Random.nextLong(250L, 750L)
        return (base + jitter).coerceAtMost(8_000L)
    }

    private fun isTransientOverloadError(error: Throwable): Boolean {
        if (error is QuotaExceededException ||
            error is ServerException ||
            error is TimeoutCancellationException ||
            error is IOException
        ) {
            return true
        }
        val msg = error.message?.lowercase().orEmpty()
        return msg.contains("429") ||
            msg.contains("503") ||
            msg.contains("504") ||
            msg.contains("overloaded") ||
            msg.contains("unavailable") ||
            msg.contains("resource exhausted") ||
            msg.contains("rate limit") ||
            msg.contains("timeout")
    }

    private fun mapFriendlyErrorMessage(error: Throwable): String {
        return when {
            error is TimeoutCancellationException ->
                "پاسخ‌گویی سرور به دلیل ترافیک بالا زمان‌بر شد. لطفاً چند لحظه بعد دوباره تلاش کنید."
            error is QuotaExceededException ->
                "سرور در حال حاضر با ترافیک بسیار بالا روبه‌رو است. لطفاً چند لحظه صبر کرده و دوباره ارسال کنید."
            error is ServerException ->
                "سرور موقتاً شلوغ است و پس از تلاش مجدد در دسترس نبود. لطفاً کمی بعد دوباره تلاش کنید."
            error is IOException ->
                "اتصال شبکه ناپایدار است. لطفاً اینترنت خود را بررسی کرده و دوباره تلاش کنید."
            else ->
                error.localizedMessage?.takeIf {
                    !it.contains("Firebase", ignoreCase = true) &&
                        !it.contains("App Check", ignoreCase = true) &&
                        !it.contains("API", ignoreCase = true)
                } ?: "خطا در پردازش درخواست. لطفاً دوباره تلاش کنید."
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
