package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AiChatModel
import com.example.data.model.AiPersona
import com.example.data.model.AiPersonas
import com.example.data.model.Conversation
import com.example.data.model.CustomAssistant
import com.example.data.model.ImageCreation
import com.example.data.model.UiState
import com.example.data.model.UsageLog
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    userDisplayName: String,
    userPhotoUrl: String?,
    onOpenNewChat: (AiPersona?) -> Unit,
    onOpenCustomAssistantChat: (CustomAssistant) -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onOpenImageStudio: () -> Unit,
    onOpenAgentsHub: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val creationsState by viewModel.creationsState.collectAsStateWithLifecycle()
    val usageLogsState by viewModel.usageLogsState.collectAsStateWithLifecycle()
    val assistantsState by viewModel.assistantsState.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val conversations = (conversationsState as? UiState.Success)?.data ?: emptyList()
    val creations = (creationsState as? UiState.Success)?.data ?: emptyList()
    val usageLogs = (usageLogsState as? UiState.Success)?.data ?: emptyList()
    val customAssistants = (assistantsState as? UiState.Success)?.data ?: emptyList()

    val avgLatencyMs = if (usageLogs.isNotEmpty()) {
        usageLogs.map { it.latencyMs }.average().toLong()
    } else 0L

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                DashboardHeader(
                    displayName = profile?.displayName ?: userDisplayName,
                    photoUrl = profile?.photoUrl ?: userPhotoUrl,
                    activeModel = AiChatModel.fromId(profile?.preferredChatModel)
                )
            }

            item {
                HeroCommandCard(
                    onStartChat = { onOpenNewChat(null) },
                    onStartImage = onOpenImageStudio
                )
            }

            item {
                RealTelemetryKpiGrid(
                    totalTokens = profile?.totalTokensUsed ?: usageLogs.sumOf { it.totalTokens },
                    totalCalls = (profile?.totalMessages ?: 0L) + (profile?.totalImages ?: 0L),
                    avgLatencyMs = avgLatencyMs,
                    totalImages = profile?.totalImages ?: creations.size.toLong()
                )
            }

            item {
                RealUsageTelemetryCard(usageLogs = usageLogs)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        title = "دستیارهای تخصصی و سفارشی",
                        subtitle = "عامل‌های هوشمند با دستورالعمل اختصاصی"
                    )
                    FilledTonalButton(
                        onClick = onOpenAgentsHub,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("مدیریت", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(customAssistants, key = { "custom_${it.id}" }) { assistant ->
                        CustomAssistantQuickCard(
                            assistant = assistant,
                            onClick = { onOpenCustomAssistantChat(assistant) }
                        )
                    }
                    items(AiPersonas.all, key = { it.id }) { persona ->
                        PersonaCard(
                            persona = persona,
                            onClick = { onOpenNewChat(persona) }
                        )
                    }
                }
            }

            if (usageLogs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "گزارش زنده تراکنش‌های API",
                        subtitle = "ثبت دقیق توکن و زمان پاسخ هر درخواست"
                    )
                }
                items(usageLogs.take(4), key = { "log_${it.id}" }) { log ->
                    UsageLogRow(log = log)
                }
            }

            item {
                SectionHeader(
                    title = "گفتگوهای اخیر ابری",
                    subtitle = "همگام‌سازی لحظه‌ای با Firestore"
                )
            }

            when (val convState = conversationsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = convState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (convState.data.isEmpty()) {
                        item {
                            EmptyRecentCard(onStartChat = { onOpenNewChat(null) })
                        }
                    } else {
                        items(convState.data.take(6), key = { it.id }) { conv ->
                            RecentConversationRow(
                                conversation = conv,
                                onClick = { onOpenConversation(conv) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    displayName: String,
    photoUrl: String?,
    activeModel: AiChatModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "تصویر پروفایل",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = displayName.firstOrNull()?.toString() ?: "ن",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "فضای کاری $displayName",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "موتور پیش‌فرض: ${activeModel.titleFa} (${activeModel.contextInfo})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = EmeraldSuccess.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmeraldSuccess)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "API فعال",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldSuccess
                )
            }
        }
    }
}

@Composable
private fun HeroCommandCard(
    onStartChat: () -> Unit,
    onStartImage: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_ai_hero),
                contentDescription = "بنر پلتفرم هوشمند نوا",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xEC060913),
                                Color(0xCC0D1324),
                                Color(0x99060913)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF22D3EE).copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Gemini 3.8 • 4 Argon • Nano Banana 2.1",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA5F3FC)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "کنسول عملیاتی هوش مصنوعی",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "اجرای استریم مدل‌های زبانی، مهندسی پرامپت ساختاریافته و رندر تصویر با پایش لحظه‌ای توکن",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartChat,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22D3EE),
                            contentColor = Color(0xFF00262E)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dash_new_chat_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ورک‌اسپیس چت", style = MaterialTheme.typography.labelLarge)
                    }
                    FilledTonalButton(
                        onClick = onStartImage,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E293B).copy(alpha = 0.9f),
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dash_new_image_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استودیو تصویر", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun RealTelemetryKpiGrid(
    totalTokens: Long,
    totalCalls: Long,
    avgLatencyMs: Long,
    totalImages: Long
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                label = "کل توکن مصرفی",
                value = formatCompactNumber(totalTokens),
                icon = Icons.Filled.Token,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "میانگین تاخیر پاسخ",
                value = if (avgLatencyMs > 0L) "${avgLatencyMs}ms" else "—",
                icon = Icons.Filled.Speed,
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                label = "کل پردازش‌های ثبت‌شده",
                value = totalCalls.toString(),
                icon = Icons.Filled.Bolt,
                accent = EmeraldSuccess,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "تصاویر رندرشده",
                value = totalImages.toString(),
                icon = Icons.Filled.Brush,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RealUsageTelemetryCard(usageLogs: List<UsageLog>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DataUsage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "نمودار واقعی مصرف توکن و درخواست‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${usageLogs.size} فراخوانی",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (usageLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "هنوز فراخوانی API ثبت نشده است. اولین پیام یا تصویر را بسازید تا نمودار واقعی رسم شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val recentLogs = usageLogs.take(8).reversed()
                val values = recentLogs.map { log ->
                    if (log.totalTokens > 0L) log.totalTokens.toFloat() else (log.latencyMs / 25f).coerceAtLeast(10f)
                }
                val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) {
                    val barCount = values.size
                    val spacing = 10.dp.toPx()
                    val barWidth = (size.width - spacing * (barCount - 1).coerceAtLeast(0)) / barCount.coerceAtLeast(1)
                    values.forEachIndexed { index, value ->
                        val left = index * (barWidth + spacing)
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        val ratio = (value / maxVal).coerceIn(0.08f, 1f)
                        val filledHeight = size.height * ratio
                        val top = size.height - filledHeight
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor, secondaryColor),
                                startY = top,
                                endY = size.height
                            ),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, filledHeight),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val modelCounts = usageLogs.groupingBy { it.modelName }.eachCount()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(modelCounts.entries.toList(), key = { it.key }) { entry ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${entry.key}: ${entry.value}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageLogRow(log: UsageLog) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.modelName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (log.serviceType) {
                        "image" -> "تولید تصویر"
                        "optimizer" -> "بهینه‌سازی پرامپت"
                        else -> "چت هوشمند"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (log.totalTokens > 0L) "${log.totalTokens} tok" else "Image",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${log.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomAssistantQuickCard(
    assistant: CustomAssistant,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .width(188.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "سفارشی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = assistant.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = assistant.roleDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PersonaCard(
    persona: AiPersona,
    onClick: () -> Unit
) {
    val icon = when (persona.id) {
        "coder" -> Icons.Filled.Code
        "writer" -> Icons.Filled.Brush
        "translator" -> Icons.Filled.Translate
        "strategist" -> Icons.Filled.Insights
        else -> Icons.Filled.AutoAwesome
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(178.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("persona_card_${persona.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = persona.titleFa,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = persona.subtitleFa,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentConversationRow(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("recent_conv_${conversation.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (conversation.pinned) Icons.Filled.PushPin else Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = conversation.lastMessagePreview.ifBlank { "گفتگوی آماده ادامه..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "باز کردن گفتگو",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EmptyRecentCard(onStartChat: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "هنوز گفتگویی ثبت نشده است",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "اولین گفتگوی خود را با جمینای ۳.۸ شروع کنید تا با آمار توکن در ابر ذخیره شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onStartChat) {
                Text("شروع اولین گفتگو")
            }
        }
    }
}

private fun formatCompactNumber(value: Long): String {
    return when {
        value >= 1_000_000L -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}
