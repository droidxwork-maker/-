package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val DEFAULT_DAILY_REQUEST_LIMIT = 50L

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class DailyQuotaExceededException(message: String) : IllegalStateException(message)

object QuotaDateHelper {
    fun todayUtc(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}

enum class AiChatModel(
    val modelId: String,
    val titleFa: String,
    val subtitleFa: String,
    val badge: String,
    val contextInfo: String
) {
    FLASH_LITE_3_5(
        modelId = "gemini-3.5-flash-lite",
        titleFa = "جمینای ۳.۵ لایت",
        subtitleFa = "پاسخ‌گویی سریع، تحلیل متن و تصویر، نگارش و کدنویسی",
        badge = "نوا ۳.۵ سریع",
        contextInfo = "نسل جدید"
    );

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromId(id: String?): AiChatModel = FLASH_LITE_3_5
    }
}

enum class AiResponseMode(
    val titleFa: String,
    val descriptionFa: String,
    val temperature: Float
) {
    BALANCED(
        titleFa = "متعادل",
        descriptionFa = "تعادل میان دقت و روانی پاسخ",
        temperature = 0.7f
    ),
    PRECISE(
        titleFa = "دقیق و کدنویسی",
        descriptionFa = "بهینه برای برنامه‌نویسی، منطق و جداول",
        temperature = 0.25f
    ),
    CREATIVE(
        titleFa = "خلاق و ادبی",
        descriptionFa = "مناسب ایده‌پردازی، سناریو و نگارش",
        temperature = 1.0f
    );

    companion object {
        fun closestTo(temp: Float): AiResponseMode {
            return entries.minByOrNull { kotlin.math.abs(it.temperature - temp) } ?: BALANCED
        }
    }
}

data class AiPersona(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val systemPrompt: String,
    val starterPrompt: String,
    val defaultModel: AiChatModel = AiChatModel.FLASH_LITE_3_5,
    val defaultTemperature: Double = 0.7,
    val isCustom: Boolean = false
)

object AiPersonas {
    val all = listOf(
        AiPersona(
            id = "general",
            titleFa = "دستیار جامع",
            subtitleFa = "پاسخگویی دقیق، ساختاریافته و تحلیلی",
            systemPrompt = "شما دستیار هوشمند پلتفرم نوا هستید. به زبان فارسی روان، دقیق، مستند و ساختاریافته پاسخ دهید. از Markdown کامل برای تیترها، جداول مقایسه‌ای، فهرست‌ها و بلوک‌های کد استاندارد استفاده کنید.",
            starterPrompt = "یک جدول مقایسه جامع بین معماری Monolith، Modular Monolith و Microservices ارائه بده."
        ),
        AiPersona(
            id = "coder",
            titleFa = "مهندس ارشد نرم‌افزار",
            subtitleFa = "معماری سیستم، کدنویسی تمیز و دیباگ",
            systemPrompt = "شما مهندس ارشد نرم‌افزار هستید. کدهای امن، بهینه و قابل نگهداری بنویسید، با مثال و بلوک کد مشخص‌شده با زبان برنامه‌نویسی توضیح دهید و ریسک‌های عملکردی و همزمانی را در قالب جدول یا نکات کلیدی گوشزد کنید.",
            starterPrompt = "یک الگوریتم Rate Limiter با Token Bucket در Kotlin بنویس و پیچیدگی زمانی آن را تحلیل کن.",
            defaultModel = AiChatModel.FLASH_LITE_3_5,
            defaultTemperature = 0.25
        ),
        AiPersona(
            id = "writer",
            titleFa = "استراتژیست محتوا",
            subtitleFa = "نگارش حرفه‌ای، سناریو و کپی‌رایتینگ",
            systemPrompt = "شما نویسنده و استراتژیست محتوای حرفه‌ای هستید. متون خلاقانه، دقیق، خوانا و متناسب با لحن تجاری بنویسید.",
            starterPrompt = "یک ساختار صفحه فرود متقاعدکننده برای یک پلتفرم هوش مصنوعی سازمانی طراحی کن.",
            defaultTemperature = 0.95
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
            systemPrompt = "شما تحلیلگر ارشد کسب‌وکار و محصول هستید. تحلیل‌های داده‌محور، شاخص‌های کلیدی (KPI) و راهکارهای عملی را با جدول و ساختار شفاف ارائه دهید.",
            starterPrompt = "ساختار قیمت‌گذاری پلکانی و سهمیه مصرف برای یک سرویس اشتراکی هوش مصنوعی پیشنهاد بده.",
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
    val dailyQuotaDate: String = "",
    val dailyRequestsUsed: Long = 0L,
    val dailyRequestLimit: Long = DEFAULT_DAILY_REQUEST_LIMIT,
    val dailyTokensUsed: Long = 0L,
    val preferredChatModel: String = AiChatModel.FLASH_LITE_3_5.modelId,
    val preferredTemperature: Double = 0.7,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun effectiveDailyUsed(todayUtc: String = QuotaDateHelper.todayUtc()): Long =
        if (dailyQuotaDate == todayUtc) dailyRequestsUsed.coerceAtLeast(0L) else 0L

    fun effectiveDailyLimit(): Long =
        if (dailyRequestLimit > 0L) dailyRequestLimit else DEFAULT_DAILY_REQUEST_LIMIT

    fun remainingDailyRequests(todayUtc: String = QuotaDateHelper.todayUtc()): Long =
        (effectiveDailyLimit() - effectiveDailyUsed(todayUtc)).coerceAtLeast(0L)

    fun effectiveDailyTokens(todayUtc: String = QuotaDateHelper.todayUtc()): Long =
        if (dailyQuotaDate == todayUtc) dailyTokensUsed.coerceAtLeast(0L) else 0L
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "گفتگوی جدید",
    val modelName: String = AiChatModel.FLASH_LITE_3_5.modelId,
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
data class CustomAssistant(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val roleDescription: String = "",
    val systemPrompt: String = "",
    val modelName: String = AiChatModel.FLASH_LITE_3_5.modelId,
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
