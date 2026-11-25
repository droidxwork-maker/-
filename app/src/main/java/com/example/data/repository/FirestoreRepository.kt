package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AiChatModel
import com.example.data.model.ChatMessageRecord
import com.example.data.model.Conversation
import com.example.data.model.CustomAssistant
import com.example.data.model.DEFAULT_DAILY_REQUEST_LIMIT
import com.example.data.model.DailyQuotaExceededException
import com.example.data.model.QuotaDateHelper
import com.example.data.model.SavedPrompt
import com.example.data.model.UsageLog
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class QuotaConsumptionResult(
    val usedToday: Long,
    val dailyLimit: Long,
    val remainingToday: Long,
    val quotaDateUtc: String
)

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("برای استفاده از خدمات باید با حساب کاربری خود وارد شوید.")
    }

    private fun userDocRef(uid: String) = db.collection("users").document(uid)
    private fun conversationsRef(uid: String) = userDocRef(uid).collection("conversations")
    private fun messagesRef(uid: String, convId: String) =
        conversationsRef(uid).document(convId).collection("messages")
    private fun assistantsRef(uid: String) = userDocRef(uid).collection("assistants")
    private fun promptsRef(uid: String) = userDocRef(uid).collection("prompts")
    private fun usageLogsRef(uid: String) = userDocRef(uid).collection("usageLogs")

    suspend fun ensureUserProfile(): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("کاربر وارد نشده است."))
        val uid = currentUser.uid
        val path = "users/$uid"
        return try {
            val snapshot = userDocRef(uid).get().await()
            val safeName = currentUser.displayName?.trim()?.take(100)
                ?.takeIf { it.isNotEmpty() } ?: "کاربر نوا"
            val safeEmail = currentUser.email?.trim()?.take(200)
            val safePhoto = currentUser.photoUrl?.toString()?.take(1000)
            val todayUtc = QuotaDateHelper.todayUtc()

            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "totalChats" to 0L,
                    "totalMessages" to 0L,
                    "totalImages" to 0L,
                    "totalTokensUsed" to 0L,
                    "dailyQuotaDate" to todayUtc,
                    "dailyRequestsUsed" to 0L,
                    "dailyRequestLimit" to DEFAULT_DAILY_REQUEST_LIMIT,
                    "dailyTokensUsed" to 0L,
                    "preferredChatModel" to AiChatModel.FLASH_LITE_3_5.modelId,
                    "preferredTemperature" to 0.7,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                userDocRef(uid).set(payload).await()
            } else {
                val existingLimit = (snapshot.get("dailyRequestLimit") as? Number)?.toLong()
                val existingDate = snapshot.getString("dailyQuotaDate")
                val existingUsed = (snapshot.get("dailyRequestsUsed") as? Number)?.toLong() ?: 0L
                val existingDailyTokens = (snapshot.get("dailyTokensUsed") as? Number)?.toLong() ?: 0L

                val updates = mutableMapOf<String, Any>(
                    "displayName" to safeName,
                    "preferredChatModel" to AiChatModel.FLASH_LITE_3_5.modelId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeEmail != null) updates["email"] = safeEmail
                if (safePhoto != null) updates["photoUrl"] = safePhoto

                if (existingLimit == null || existingDate.isNullOrBlank()) {
                    updates["dailyQuotaDate"] = todayUtc
                    updates["dailyRequestsUsed"] = 0L
                    updates["dailyRequestLimit"] = DEFAULT_DAILY_REQUEST_LIMIT
                    updates["dailyTokensUsed"] = 0L
                } else if (existingDate != todayUtc && (existingUsed > 0L || existingDailyTokens > 0L)) {
                    updates["dailyQuotaDate"] = todayUtc
                    updates["dailyRequestsUsed"] = 0L
                    updates["dailyTokensUsed"] = 0L
                }

                userDocRef(uid).update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically reserves 1 unit from the user's daily request quota inside a Firestore transaction.
     * Automatically resets usage counters when the UTC day rolls over.
     */
    suspend fun consumeDailyQuotaIfAvailable(): Result<QuotaConsumptionResult> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            path = userDocRef(uid).path
            val todayUtc = QuotaDateHelper.todayUtc()

            val quotaResult = db.runTransaction { transaction ->
                val snapshot = transaction.get(userDocRef(uid))
                if (!snapshot.exists()) {
                    throw IllegalStateException("پروفایل کاربری هنوز آماده نشده است. لطفاً لحظه‌ای صبر کنید.")
                }

                val limit = ((snapshot.get("dailyRequestLimit") as? Number)?.toLong()
                    ?: DEFAULT_DAILY_REQUEST_LIMIT).coerceIn(1L, 500L)
                val storedDate = snapshot.getString("dailyQuotaDate").orEmpty()
                val storedUsed = ((snapshot.get("dailyRequestsUsed") as? Number)?.toLong() ?: 0L)
                    .coerceAtLeast(0L)
                val storedDailyTokens = ((snapshot.get("dailyTokensUsed") as? Number)?.toLong() ?: 0L)
                    .coerceAtLeast(0L)

                val isNewDay = storedDate != todayUtc
                val currentUsed = if (isNewDay) 0L else storedUsed

                if (currentUsed >= limit) {
                    throw DailyQuotaExceededException(
                        "سهمیه روزانه شما ($limit درخواست در روز) به پایان رسیده است. سهمیه شما به‌صورت خودکار در روز جدید تمدید می‌شود."
                    )
                }

                val nextUsed = currentUsed + 1L
                val updates = mutableMapOf<String, Any>(
                    "dailyQuotaDate" to todayUtc,
                    "dailyRequestsUsed" to nextUsed,
                    "dailyTokensUsed" to if (isNewDay) 0L else storedDailyTokens,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (snapshot.get("dailyRequestLimit") == null) {
                    updates["dailyRequestLimit"] = limit
                }
                transaction.update(userDocRef(uid), updates)

                QuotaConsumptionResult(
                    usedToday = nextUsed,
                    dailyLimit = limit,
                    remainingToday = (limit - nextUsed).coerceAtLeast(0L),
                    quotaDateUtc = todayUtc
                )
            }.await()

            Result.success(quotaResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DailyQuotaExceededException) {
            Result.failure(e)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Safely refunds 1 unit of daily quota when a request fails before generating any output.
     */
    suspend fun refundDailyQuotaUnit(quotaDateUtc: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "users/$uid"
        return try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userDocRef(uid))
                if (!snapshot.exists()) return@runTransaction
                val storedDate = snapshot.getString("dailyQuotaDate").orEmpty()
                val storedUsed = (snapshot.get("dailyRequestsUsed") as? Number)?.toLong() ?: 0L
                if (storedDate == quotaDateUtc && storedUsed > 0L) {
                    transaction.update(
                        userDocRef(uid),
                        mapOf(
                            "dailyRequestsUsed" to (storedUsed - 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            userDocRef(userId).snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.GET, path, userId)
                }
        )
    }

    suspend fun updatePreferredTemperature(temperature: Double): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("کاربر وارد نشده است."))
        val safeTemp = temperature.coerceIn(0.0, 2.0)
        val path = "users/$uid"
        return try {
            userDocRef(uid).update(
                mapOf(
                    "preferredTemperature" to safeTemp,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/conversations"
        emitAll(
            conversationsRef(userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.pinned }
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessageRecord>> = flow {
        require(userId.isNotBlank() && conversationId.isNotBlank()) { "IDs must not be blank" }
        val path = "users/$userId/conversations/$conversationId/messages"
        emitAll(
            messagesRef(userId, conversationId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessageRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, userId)
                }
        )
    }

    suspend fun createConversation(
        title: String,
        modelName: String,
        personaId: String,
        systemPromptOverride: String? = null,
        temperature: Double = 0.7
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().take(120).ifEmpty { "گفتگوی جدید" }
            val safeModel = AiChatModel.fromId(modelName).modelId
            val safePersona = personaId.trim().take(80).ifEmpty { "general" }
            val safeTemp = temperature.coerceIn(0.0, 2.0)

            val docRef = conversationsRef(uid).document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "modelName" to safeModel,
                "personaId" to safePersona,
                "systemPromptOverride" to systemPromptOverride?.trim()?.take(2000)?.takeIf { it.isNotEmpty() },
                "temperature" to safeTemp,
                "pinned" to false,
                "lastMessagePreview" to "",
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val batch = db.batch()
            batch.set(docRef, payload)
            batch.update(
                userDocRef(uid),
                mapOf(
                    "totalChats" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameConversation(conversationId: String, newTitle: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val safeTitle = newTitle.trim().take(120)
            require(safeTitle.isNotEmpty()) { "عنوان گفتگو نمی‌تواند خالی باشد" }
            val ref = conversationsRef(uid).document(conversationId)
            path = ref.path
            ref.update(
                mapOf(
                    "title" to safeTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleConversationPin(conversationId: String, pinned: Boolean): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val ref = conversationsRef(uid).document(conversationId)
            path = ref.path
            ref.update(
                mapOf(
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationParams(
        conversationId: String,
        temperature: Double,
        systemPromptOverride: String?
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val ref = conversationsRef(uid).document(conversationId)
            path = ref.path
            val cleanOverride = systemPromptOverride?.trim()?.take(2000).orEmpty()
            ref.update(
                mapOf(
                    "temperature" to temperature.coerceIn(0.0, 2.0),
                    "systemPromptOverride" to cleanOverride,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically writes the message, updates conversation metadata, increments user counters,
     * and appends usage log in a single Firestore WriteBatch for high-load stability.
     */
    suspend fun addMessageAndUpdateConversation(
        conversationId: String,
        role: String,
        text: String,
        modelName: String?,
        hasImageAttachment: Boolean,
        promptTokens: Long = 0L,
        completionTokens: Long = 0L,
        totalTokens: Long = 0L,
        latencyMs: Long = 0L,
        newTitleIfFirst: String? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
            val safeRole = if (role == "model") "model" else "user"
            val safeText = text.trim().take(30000).ifEmpty { "..." }
            val msgRef = messagesRef(uid, conversationId).document()
            path = msgRef.path

            val safeModel = AiChatModel.fromId(modelName).modelId
            val safePromptTok = promptTokens.coerceAtLeast(0L)
            val safeCompTok = completionTokens.coerceAtLeast(0L)
            val safeTotalTok = totalTokens.coerceAtLeast(0L)
            val safeLatency = latencyMs.coerceAtLeast(0L)

            val msgPayload = mapOf(
                "userId" to uid,
                "conversationId" to conversationId,
                "role" to safeRole,
                "text" to safeText,
                "modelName" to safeModel,
                "hasImageAttachment" to hasImageAttachment,
                "promptTokens" to safePromptTok,
                "completionTokens" to safeCompTok,
                "totalTokens" to safeTotalTok,
                "latencyMs" to safeLatency,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val convUpdates = mutableMapOf<String, Any>(
                "lastMessagePreview" to safeText.replace("\n", " ").take(280),
                "messageCount" to FieldValue.increment(1L),
                "modelName" to safeModel,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newTitleIfFirst.isNullOrBlank()) {
                convUpdates["title"] = newTitleIfFirst.trim().take(120)
            }

            val userUpdates = mutableMapOf<String, Any>(
                "totalMessages" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (safeTotalTok > 0L) {
                userUpdates["totalTokensUsed"] = FieldValue.increment(safeTotalTok)
                userUpdates["dailyTokensUsed"] = FieldValue.increment(safeTotalTok)
            }

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(conversationsRef(uid).document(conversationId), convUpdates)
            batch.update(userDocRef(uid), userUpdates)

            if (safeRole == "model") {
                val logRef = usageLogsRef(uid).document()
                batch.set(
                    logRef,
                    mapOf(
                        "userId" to uid,
                        "serviceType" to "chat",
                        "modelName" to safeModel,
                        "promptTokens" to safePromptTok,
                        "completionTokens" to safeCompTok,
                        "totalTokens" to safeTotalTok,
                        "latencyMs" to safeLatency,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val ref = messagesRef(uid, conversationId).document(messageId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
            path = conversationsRef(uid).document(conversationId).path
            val msgSnapshots = messagesRef(uid, conversationId).get().await()
            val batch = db.batch()
            for (doc in msgSnapshots.documents) {
                batch.delete(doc.reference)
            }
            batch.delete(conversationsRef(uid).document(conversationId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Custom Assistants CRUD ---
    fun observeAssistants(userId: String): Flow<List<CustomAssistant>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/assistants"
        emitAll(
            assistantsRef(userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomAssistant::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, userId)
                }
        )
    }

    suspend fun saveAssistant(
        existingId: String?,
        name: String,
        roleDescription: String,
        systemPrompt: String,
        modelName: String,
        temperature: Double
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/assistants"
        return try {
            val uid = requireUserId()
            val safeName = name.trim().take(60)
            val safeRole = roleDescription.trim().take(160)
            val safePrompt = systemPrompt.trim().take(2000)
            require(safeName.isNotEmpty() && safeRole.isNotEmpty() && safePrompt.isNotEmpty()) {
                "نام، شرح نقش و دستورالعمل سیستمی الزامی هستند."
            }
            val safeModel = AiChatModel.fromId(modelName).modelId
            val safeTemp = temperature.coerceIn(0.0, 2.0)

            if (existingId.isNullOrBlank()) {
                val ref = assistantsRef(uid).document()
                path = ref.path
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "name" to safeName,
                        "roleDescription" to safeRole,
                        "systemPrompt" to safePrompt,
                        "modelName" to safeModel,
                        "temperature" to safeTemp,
                        "usageCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(ref.id)
            } else {
                val ref = assistantsRef(uid).document(existingId)
                path = ref.path
                ref.update(
                    mapOf(
                        "name" to safeName,
                        "roleDescription" to safeRole,
                        "systemPrompt" to safePrompt,
                        "modelName" to safeModel,
                        "temperature" to safeTemp,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(existingId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementAssistantUsage(assistantId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/assistants/$assistantId"
        return try {
            val uid = requireUserId()
            val ref = assistantsRef(uid).document(assistantId)
            path = ref.path
            ref.update(
                mapOf(
                    "usageCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAssistant(assistantId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/assistants/$assistantId"
        return try {
            val uid = requireUserId()
            val ref = assistantsRef(uid).document(assistantId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Saved Prompt Library CRUD ---
    fun observeSavedPrompts(userId: String): Flow<List<SavedPrompt>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/prompts"
        emitAll(
            promptsRef(userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedPrompt::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, userId)
                }
        )
    }

    suspend fun savePromptTemplate(
        existingId: String?,
        title: String,
        category: String,
        content: String
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/prompts"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().take(80)
            val safeCategory = category.trim().take(40).ifEmpty { "عمومی" }
            val safeContent = content.trim().take(2000)
            require(safeTitle.isNotEmpty() && safeContent.isNotEmpty()) {
                "عنوان و متن پرامپت نمی‌توانند خالی باشند."
            }

            if (existingId.isNullOrBlank()) {
                val ref = promptsRef(uid).document()
                path = ref.path
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "title" to safeTitle,
                        "category" to safeCategory,
                        "content" to safeContent,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(ref.id)
            } else {
                val ref = promptsRef(uid).document(existingId)
                path = ref.path
                ref.update(
                    mapOf(
                        "title" to safeTitle,
                        "category" to safeCategory,
                        "content" to safeContent,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(existingId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedPrompt(promptId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/prompts/$promptId"
        return try {
            val uid = requireUserId()
            val ref = promptsRef(uid).document(promptId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Usage Telemetry Ledger ---
    fun observeUsageLogs(userId: String): Flow<List<UsageLog>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/usageLogs"
        emitAll(
            usageLogsRef(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UsageLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, userId)
                }
        )
    }

    suspend fun recordOptimizerUsage(
        modelName: String,
        promptTokens: Long,
        completionTokens: Long,
        totalTokens: Long,
        latencyMs: Long
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val safeModel = AiChatModel.fromId(modelName).modelId
            val safeTotal = totalTokens.coerceAtLeast(0L)
            val batch = db.batch()
            val logRef = usageLogsRef(uid).document()
            batch.set(
                logRef,
                mapOf(
                    "userId" to uid,
                    "serviceType" to "optimizer",
                    "modelName" to safeModel,
                    "promptTokens" to promptTokens.coerceAtLeast(0L),
                    "completionTokens" to completionTokens.coerceAtLeast(0L),
                    "totalTokens" to safeTotal,
                    "latencyMs" to latencyMs.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            if (safeTotal > 0L) {
                batch.update(
                    userDocRef(uid),
                    mapOf(
                        "totalTokensUsed" to FieldValue.increment(safeTotal),
                        "dailyTokensUsed" to FieldValue.increment(safeTotal),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/usageLogs")
            Result.failure(e)
        }
    }

    private fun unwrapAndHandleStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val toLog = firestoreError ?: (error as? Exception)
        if (toLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(toLog, operationType, path)
        }
        throw error
    }
}
