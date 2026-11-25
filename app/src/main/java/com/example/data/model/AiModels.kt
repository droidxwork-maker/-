package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class AiChatModel(
    val modelId: String,
    val titleFa: String,
    val subtitleFa: String,
    val badge: String,
    val contextInfo: String
) {
    FLASH_3_8(
        modelId = "gemini-3.8-flash",
        titleFa = "جمینای ۳.۸ فلش",
        subtitleFa = "متعادل، سریع و دقیق برای وظایف عمومی و تحلیلی",
        badge = "3.8 Flash",
        contextInfo = "1M Context"
    ),
    FLASH_LITE_3_5(
        modelId = "gemini-3.5-flash-lite",
        titleFa = "جمینای ۳.۵ لایت",
        subtitleFa = "کمترین تأخیر و بالاترین سرعت برای پردازش‌های پرتکرار",
        badge = "3.5 Lite",
        contextInfo = "Ultra-Fast"
    ),
    ARGON_4(
        modelId = "gemini-4-argon",
        titleFa = "جمینای ۴ آرگون",
        subtitleFa = "استدلال عمیق، مهندسی نرم‌افزار و حل مسائل پیچیده",
        badge = "4 Argon",
        contextInfo = "Pro Reasoning"
    );

    companion object {
        fun fromId(id: String?): AiChatModel =
            entries.firstOrNull { it.modelId == id } ?: FLASH_3_8
    }
}

enum class AiImageModel(
    val modelId: String,
    val titleFa: String,
    val subtitleFa: String
) {
    NANO_BANANA_2_1(
        modelId = "gemini-nano-banana-2.1",
        titleFa = "نانو بنانا ۲.۱",
        subtitleFa = "رندر سریع و دقیق (پیشنهادی)"
    ),
    PRO_IMAGE_3(
        modelId = "gemini-3-pro-image",
        titleFa = "جمینای ۳ پرو ایمیج",
        subtitleFa = "کیفیت استودیویی و جزئیات سینمایی"
    );

    companion object {
        fun fromId(id: String?): AiImageModel =
            entries.firstOrNull { it.modelId == id } ?: NANO_BANANA_2_1
    }
}

enum class ImageAspectOption(
    val label: String,
    val ratioValue: String
) {
    SQUARE("مربع ۱:۱", "1:1"),
    LANDSCAPE("عریض ۱۶:۹", "16:9"),
    PORTRAIT("عمودی ۹:۱۶", "9:16"),
    CLASSIC("کلاسیک ۴:۳", "4:3"),
    TALL("پوستر ۳:۴", "3:4")
}

data class AiPersona(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val systemPrompt: String,
    val starterPrompt: String,
    val defaultModel: AiChatModel = AiChatModel.FLASH_3_8,
    val defaultTemperature: Double = 0.7,
    val isCustom: Boolean = false
)

object AiPersonas {
    val all = listOf(
        AiPersona(
            id = "general",
            titleFa = "دستیار جامع",
            subtitleFa = "پاسخگویی دقیق، ساختاریافته و تحلیلی",
            systemPrompt = "شما دستیار هوشمند پلتفرم نوا هستید. به زبان فارسی روان، دقیق، مستند و ساختاریافته پاسخ دهید. از Markdown استاندارد برای تیترها، فهرست‌ها و کدها استفاده کنید.",
            starterPrompt = "یک چک‌لیست اجرایی برای راه‌اندازی محصول نرم‌افزاری B2B طراحی کن."
        ),
        AiPersona(
            id = "coder",
            titleFa = "مهندس ارشد نرم‌افزار",
            subtitleFa = "معماری سیستم، کدنویسی تمیز و دیباگ",
            systemPrompt = "شما مهندس ارشد نرم‌افزار هستید. کدهای امن، بهینه و قابل نگهداری بنویسید، با مثال و بلوک کد استاندارد توضیح دهید و ریسک‌های عملکردی را گوشزد کنید.",
            starterPrompt = "یک الگوریتم Rate Limiter با Token Bucket در Kotlin بنویس.",
            defaultModel = AiChatModel.ARGON_4,
            defaultTemperature = 0.3
        ),
        AiPersona(
            id = "writer",
            titleFa = "استراتژیست محتوا",
            subtitleFa = "نگارش حرفه‌ای، سناریو و کپی‌رایتینگ",
            systemPrompt = "شما نویسنده و استراتژیست محتوای حرفه‌ای هستید. متون خلاقانه، دقیق و متناسب با لحن برند بنویسید.",
            starterPrompt = "یک لندینگ‌پیج متقاعدکننده برای سرویس هوش مصنوعی سازمانی بنویس.",
            defaultTemperature = 0.9
        ),
        AiPersona(
            id = "translator",
            titleFa = "مترجم تخصصی",
            subtitleFa = "ترجمه دقیق فنی و بازاریابی با حفظ لحن",
            systemPrompt = "شما مترجم حرفه‌ای متون فنی، دانشگاهی و تجاری هستید. اصطلاحات تخصصی را دقیق بومی‌سازی کنید و روان بنویسید.",
            starterPrompt = "این متن فنی را به فارسی روان و دقیق ترجمه کن:",
            defaultTemperature = 0.3
        ),
        AiPersona(
            id = "strategist",
            titleFa = "تحلیلگر کسب‌وکار",
            subtitleFa = "مدل درآمدی، تحلیل بازار و رشد محصول",
            systemPrompt = "شما تحلیلگر ارشد کسب‌وکار و محصول هستید. تحلیل‌های داده‌محور، شاخص‌های کلیدی (KPI) و راهکارهای عملی ارائه دهید.",
            starterPrompt = "ساختار قیمت‌گذاری پلکانی برای یک پلتفرم SaaS هوش مصنوعی پیشنهاد بده.",
            defaultTemperature = 0.5
        )
    )

    fun byId(id: String?): AiPersona =
        all.firstOrNull { it.id == id } ?: all.first()
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "کاربر نوا",
    val email: String? = null,
    val photoUrl: String? = null,
    val totalChats: Long = 0L,
    val totalMessages: Long = 0L,
    val totalImages: Long = 0L,
    val totalTokensUsed: Long = 0L,
    val preferredChatModel: String = AiChatModel.FLASH_3_8.modelId,
    val preferredTemperature: Double = 0.7,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "گفتگوی جدید",
    val modelName: String = AiChatModel.FLASH_3_8.modelId,
    val personaId: String = "general",
    val systemPromptOverride: String? = null,
    val temperature: Double = 0.7,
    val pinned: Boolean = false,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val text: String = "",
    val modelName: String? = null,
    val hasImageAttachment: Boolean = false,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val latencyMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ImageCreation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val prompt: String = "",
    val negativePrompt: String? = null,
    val stylePreset: String = "واقع‌گرایانه",
    val aspectRatio: String = "1:1",
    val modelName: String = AiImageModel.NANO_BANANA_2_1.modelId,
    val localFileName: String? = null,
    val editedFromSource: Boolean = false,
    val latencyMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomAssistant(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val roleDescription: String = "",
    val systemPrompt: String = "",
    val modelName: String = AiChatModel.FLASH_3_8.modelId,
    val temperature: Double = 0.7,
    val usageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toPersona(): AiPersona = AiPersona(
        id = "custom_$id",
        titleFa = name,
        subtitleFa = roleDescription,
        systemPrompt = systemPrompt,
        starterPrompt = "سلام! بر اساس دستورالعمل تنظیم‌شده آماده‌ام.",
        defaultModel = AiChatModel.fromId(modelName),
        defaultTemperature = temperature,
        isCustom = true
    )
}

@Keep
@IgnoreExtraProperties
data class SavedPrompt(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = "عمومی",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UsageLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val serviceType: String = "chat",
    val modelName: String = "",
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val latencyMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class PromptOptimizationResult(
    val improvedPrompt: String,
    val rationale: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val latencyMs: Long
)
