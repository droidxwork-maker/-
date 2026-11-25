package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AiChatModel
import com.example.data.model.AiImageModel
import com.example.data.model.ChatMessageRecord
import com.example.data.model.Conversation
import com.example.data.model.CustomAssistant
import com.example.data.model.ImageCreation
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
            ?: throw IllegalStateException("کاربر باید با گوگل وارد شده باشد.")
    }

    private fun userDocRef(uid: String) = db.collection("users").document(uid)
    private fun conversationsRef(uid: String) = userDocRef(uid).collection("conversations")
    private fun messagesRef(uid: String, convId: String) =
        conversationsRef(uid).document(convId).collection("messages")
    private fun imageCreationsRef(uid: String) = userDocRef(uid).collection("imageCreations")
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
                    "preferredChatModel" to AiChatModel.FLASH_3_8.modelId,
                    "preferredTemperature" to 0.7,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                userDocRef(uid).set(payload).await()
            } else {
                val updates = mapOf(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
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

    suspend fun updatePreferredChatModel(modelId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("کاربر وارد نشده است."))
        val validModel = AiChatModel.fromId(modelId).modelId
        val path = "users/$uid"
        return try {
            userDocRef(uid).update(
                mapOf(
                    "preferredChatModel" to validModel,
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
            docRef.set(payload).await()
            userDocRef(uid).update(
                mapOf(
                    "totalChats" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
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

            val safePromptTok = promptTokens.coerceAtLeast(0L)
            val safeCompTok = completionTokens.coerceAtLeast(0L)
            val safeTotalTok = totalTokens.coerceAtLeast(0L)
            val safeLatency = latencyMs.coerceAtLeast(0L)

            val msgPayload = mapOf(
                "userId" to uid,
                "conversationId" to conversationId,
                "role" to safeRole,
                "text" to safeText,
                "modelName" to modelName?.take(80),
                "hasImageAttachment" to hasImageAttachment,
                "promptTokens" to safePromptTok,
                "completionTokens" to safeCompTok,
                "totalTokens" to safeTotalTok,
                "latencyMs" to safeLatency,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            msgRef.set(msgPayload).await()

            val convUpdates = mutableMapOf<String, Any>(
                "lastMessagePreview" to safeText.replace("\n", " ").take(280),
                "messageCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newTitleIfFirst.isNullOrBlank()) {
                convUpdates["title"] = newTitleIfFirst.trim().take(120)
            }
            if (!modelName.isNullOrBlank()) {
                convUpdates["modelName"] = AiChatModel.fromId(modelName).modelId
            }
            conversationsRef(uid).document(conversationId).update(convUpdates).await()

            val userUpdates = mutableMapOf<String, Any>(
                "totalMessages" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (safeTotalTok > 0L) {
                userUpdates["totalTokensUsed"] = FieldValue.increment(safeTotalTok)
            }
            userDocRef(uid).update(userUpdates).await()

            if (safeRole == "model" && !modelName.isNullOrBlank()) {
                recordUsageLogInternal(
                    uid = uid,
                    serviceType = "chat",
                    modelName = modelName,
                    promptTokens = safePromptTok,
                    completionTokens = safeCompTok,
                    totalTokens = safeTotalTok,
                    latencyMs = safeLatency
                )
            }

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
            for (doc in msgSnapshots.documents) {
                doc.reference.delete().await()
            }
            conversationsRef(uid).document(conversationId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeImageCreations(userId: String): Flow<List<ImageCreation>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/imageCreations"
        emitAll(
            imageCreationsRef(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ImageCreation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndHandleStreamError(error, OperationType.LIST, path, userId)
                }
        )
    }

    suspend fun recordImageCreation(
        prompt: String,
        negativePrompt: String?,
        stylePreset: String,
        aspectRatio: String,
        modelName: String,
        localFileName: String?,
        editedFromSource: Boolean,
        promptTokens: Long,
        completionTokens: Long,
        totalTokens: Long,
        latencyMs: Long
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/imageCreations"
        return try {
            val uid = requireUserId()
            val docRef = imageCreationsRef(uid).document()
            path = docRef.path
            val safeNeg = negativePrompt?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
            val safeLatency = latencyMs.coerceAtLeast(0L)
            val payload = mapOf(
                "userId" to uid,
                "prompt" to prompt.trim().take(1000).ifEmpty { "تصویر هوشمند" },
                "negativePrompt" to safeNeg,
                "stylePreset" to stylePreset.trim().take(60).ifEmpty { "واقع‌گرایانه" },
                "aspectRatio" to aspectRatio.trim().take(20).ifEmpty { "1:1" },
                "modelName" to AiImageModel.fromId(modelName).modelId,
                "localFileName" to localFileName?.take(200),
                "editedFromSource" to editedFromSource,
                "latencyMs" to safeLatency,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()

            val userUpdates = mutableMapOf<String, Any>(
                "totalImages" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (totalTokens > 0L) {
                userUpdates["totalTokensUsed"] = FieldValue.increment(totalTokens)
            }
            userDocRef(uid).update(userUpdates).await()

            recordUsageLogInternal(
                uid = uid,
                serviceType = "image",
                modelName = modelName,
                promptTokens = promptTokens.coerceAtLeast(0L),
                completionTokens = completionTokens.coerceAtLeast(0L),
                totalTokens = totalTokens.coerceAtLeast(0L),
                latencyMs = safeLatency
            )

            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteImageCreation(creationId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/imageCreations/$creationId"
        return try {
            val uid = requireUserId()
            require(creationId.isNotBlank()) { "Creation ID must not be blank" }
            val ref = imageCreationsRef(uid).document(creationId)
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

    // --- Real Usage Telemetry Ledger ---
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
            recordUsageLogInternal(
                uid = uid,
                serviceType = "optimizer",
                modelName = modelName,
                promptTokens = promptTokens.coerceAtLeast(0L),
                completionTokens = completionTokens.coerceAtLeast(0L),
                totalTokens = totalTokens.coerceAtLeast(0L),
                latencyMs = latencyMs.coerceAtLeast(0L)
            )
            if (totalTokens > 0L) {
                userDocRef(uid).update(
                    mapOf(
                        "totalTokensUsed" to FieldValue.increment(totalTokens),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/usageLogs")
            Result.failure(e)
        }
    }

    private suspend fun recordUsageLogInternal(
        uid: String,
        serviceType: String,
        modelName: String,
        promptTokens: Long,
        completionTokens: Long,
        totalTokens: Long,
        latencyMs: Long
    ) {
        val ref = usageLogsRef(uid).document()
        ref.set(
            mapOf(
                "userId" to uid,
                "serviceType" to serviceType,
                "modelName" to modelName.take(80),
                "promptTokens" to promptTokens,
                "completionTokens" to completionTokens,
                "totalTokens" to totalTokens,
                "latencyMs" to latencyMs,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
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
