package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AiPersonas
import com.example.data.model.AiResponseMode
import com.example.data.model.ChatMessageRecord
import com.example.data.model.Conversation
import com.example.data.model.DEFAULT_DAILY_REQUEST_LIMIT
import com.example.data.model.QuotaDateHelper
import com.example.data.model.UiState
import com.example.ui.components.MarkdownMessageView
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.ChatViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val selectedPersona by viewModel.selectedPersona.collectAsStateWithLifecycle()
    val selectedConvId by viewModel.selectedConversationId.collectAsStateWithLifecycle()
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val systemOverride by viewModel.systemPromptOverride.collectAsStateWithLifecycle()
    val prefillDraft by viewModel.prefillDraft.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val retryStatus by viewModel.retryStatus.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val attachedBitmap by viewModel.attachedBitmap.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val todayUtc = remember { QuotaDateHelper.todayUtc() }
    val dailyLimit = userProfile?.effectiveDailyLimit() ?: DEFAULT_DAILY_REQUEST_LIMIT
    val remainingToday = userProfile?.remainingDailyRequests(todayUtc) ?: dailyLimit
    val activeResponseMode = remember(temperature) { AiResponseMode.closestTo(temperature) }

    var inputText by rememberSaveable { mutableStateOf("") }
    var searchHistoryQuery by remember { mutableStateOf("") }
    var showModeMenu by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showParamsDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var copiedNotice by remember { mutableStateOf(false) }

    LaunchedEffect(prefillDraft) {
        val draft = prefillDraft
        if (!draft.isNullOrBlank()) {
            inputText = draft
            viewModel.consumePrefillDraft()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImageFromUri(context, uri)
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
    val conversations = (conversationsState as? UiState.Success)?.data ?: emptyList()
    val activeConversation = conversations.firstOrNull { it.id == selectedConvId }

    LaunchedEffect(messages.size, streamingReply) {
        val total = messages.size + if (streamingReply != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize()
        ) {
            // Top Controls Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showHistorySheet = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_history_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "تاریخچه گفتگوها"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.startNewChat() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_new_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddComment,
                                contentDescription = "گفتگوی جدید"
                            )
                        }
                        IconButton(
                            onClick = { showParamsDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_params_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "تنظیمات گفتگو"
                            )
                        }
                        if (messages.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val md = viewModel.buildMarkdownExport(
                                        title = activeConversation?.title ?: "گفتگوی نوا هوش",
                                        messages = messages
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, md)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری گفتگو"))
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_export_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "اشتراک‌گذاری گفتگو"
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Daily Quota Pill
                        val quotaColor = when {
                            remainingToday == 0L -> MaterialTheme.colorScheme.error
                            remainingToday <= 5L -> AmberSpark
                            else -> EmeraldSuccess
                        }
                        Surface(
                            shape = CircleShape,
                            color = quotaColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, quotaColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "$remainingToday/$dailyLimit",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = quotaColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        // Response Mode Selector
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { showModeMenu = true }
                                    .testTag("chat_mode_selector")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = activeResponseMode.titleFa,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "تغییر حالت پاسخگویی",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false }
                            ) {
                                AiResponseMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = mode.titleFa,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = mode.descriptionFa,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectResponseMode(mode)
                                            showModeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Persona strip for new chat
            if (selectedConvId == null) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AiPersonas.all, key = { it.id }) { persona ->
                        FilterChip(
                            selected = selectedPersona.id == persona.id,
                            onClick = { viewModel.selectPersona(persona) },
                            label = { Text(persona.titleFa) }
                        )
                    }
                }
            }

            if (retryStatus != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = retryStatus.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "بستن خطا",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (copiedNotice) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    copiedNotice = false
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "در کلیپ‌بورد کپی شد",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Message Thread
            Box(modifier = Modifier.weight(1f)) {
                when (val state = messagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty() && streamingReply == null) {
                            ChatEmptyState(
                                personaTitle = selectedPersona.titleFa,
                                modeTitle = activeResponseMode.titleFa,
                                remainingToday = remainingToday,
                                dailyLimit = dailyLimit,
                                starterPrompt = selectedPersona.starterPrompt,
                                onUseStarter = { inputText = selectedPersona.starterPrompt }
                            )
                        } else {
                            val lastModelId = state.data.lastOrNull { it.role == "model" }?.id
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(state.data, key = { it.id }) { msg ->
                                    ChatMessageItem(
                                        message = msg,
                                        isLastModelMessage = msg.id == lastModelId && !isSending,
                                        onCopy = {
                                            clipboard.setText(AnnotatedString(msg.text))
                                            copiedNotice = true
                                        },
                                        onCodeCopied = { copiedNotice = true },
                                        onRegenerate = { viewModel.regenerateLastResponse() }
                                    )
                                }
                                if (streamingReply != null) {
                                    item(key = "streaming_bubble") {
                                        BorderlessStreamingMessage(
                                            text = streamingReply.orEmpty(),
                                            isRetrying = retryStatus != null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Attached Image Preview
            if (attachedBitmap != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = attachedBitmap!!.asImageBitmap(),
                            contentDescription = "تصویر پیوست‌شده",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "تصویر آماده تحلیل هوشمند",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearAttachment() }) {
                            Icon(Icons.Filled.Close, contentDescription = "حذف تصویر")
                        }
                    }
                }
            }

            // Bottom Composer
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (remainingToday == 0L) {
                        Text(
                            text = "سهمیه روزانه امروز به پایان رسیده است و فردا به‌صورت خودکار تمدید می‌شود.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isSending && remainingToday > 0L,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_attach_image_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = "افزودن تصویر",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.chat_input_placeholder),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            maxLines = 5,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isSending) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.stopStreaming() },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("chat_stop_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Stop,
                                        contentDescription = "توقف تولید پاسخ",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else {
                            val canSend = (inputText.isNotBlank() || attachedBitmap != null) && remainingToday > 0L
                            Surface(
                                shape = CircleShape,
                                color = if (canSend) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val toSend = inputText
                                        inputText = ""
                                        viewModel.sendMessage(toSend)
                                    },
                                    enabled = canSend,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("chat_send_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "ارسال پیام",
                                        tint = if (canSend) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تاریخچه گفتگوها",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        viewModel.startNewChat()
                        showHistorySheet = false
                    }) {
                        Text("گفتگوی جدید +")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchHistoryQuery,
                    onValueChange = { searchHistoryQuery = it },
                    placeholder = { Text("جستجو در گفتگوها...") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                val filtered = conversations.filter {
                    searchHistoryQuery.isBlank() ||
                        it.title.contains(searchHistoryQuery, ignoreCase = true) ||
                        it.lastMessagePreview.contains(searchHistoryQuery, ignoreCase = true)
                }
                if (filtered.isEmpty()) {
                    Text(
                        text = "گفتگویی یافت نشد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        items(filtered, key = { it.id }) { conv ->
                            HistoryConversationItem(
                                conversation = conv,
                                isSelected = conv.id == selectedConvId,
                                onSelect = {
                                    viewModel.selectConversation(conv)
                                    showHistorySheet = false
                                },
                                onTogglePin = { viewModel.togglePinConversation(conv) },
                                onRename = { renameTarget = conv },
                                onDelete = { viewModel.deleteConversation(conv.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showParamsDialog) {
        var tempDraft by remember(temperature) { mutableFloatStateOf(temperature) }
        var sysDraft by remember(systemOverride) { mutableStateOf(systemOverride) }

        AlertDialog(
            onDismissRequest = { showParamsDialog = false },
            title = { Text("شخصی‌سازی پاسخ‌دهی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "میزان خلاقیت: ${String.format(Locale.US, "%.2f", tempDraft)}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = tempDraft,
                        onValueChange = { tempDraft = it },
                        valueRange = 0f..1.5f
                    )
                    Text(
                        text = "مقادیر پایین برای دقت فنی و کدنویسی و مقادیر بالا برای ایده‌پردازی مناسب است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = sysDraft,
                        onValueChange = { sysDraft = it },
                        label = { Text("دستورالعمل اختصاصی گفتگو (اختیاری)") },
                        placeholder = { Text(selectedPersona.systemPrompt) },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateParameters(tempDraft, sysDraft)
                    showParamsDialog = false
                }) {
                    Text("اعمال")
                }
            },
            dismissButton = {
                TextButton(onClick = { showParamsDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    if (renameTarget != null) {
        var titleInput by remember(renameTarget) { mutableStateOf(renameTarget!!.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("تغییر نام گفتگو") },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameConversation(renameTarget!!.id, titleInput)
                        renameTarget = null
                    },
                    enabled = titleInput.isNotBlank()
                ) {
                    Text("ذخیره")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("لغو")
                }
            }
        )
    }
}

@Composable
private fun ChatEmptyState(
    personaTitle: String,
    modeTitle: String,
    remainingToday: Long,
    dailyLimit: Long,
    starterPrompt: String,
    onUseStarter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = personaTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "حالت $modeTitle • سهمیه باقی‌مانده امروز: $remainingToday از $dailyLimit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "پاسخ‌ها با خوانایی بالا، جداول کامل و رنگ‌آمیزی هوشمند کدها ارائه می‌شوند.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onUseStarter)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "پیشنهاد شروع: $starterPrompt",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessageRecord,
    isLastModelMessage: Boolean,
    onCopy: () -> Unit,
    onCodeCopied: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"
    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 6.dp
                ),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 560.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (message.hasImageAttachment) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تصویر پیوست‌شده",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.5.sp,
                                lineHeight = 26.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    } else {
        // Modern Borderless AI Message (No card, no border, direct on canvas)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "نوا هوش",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (message.latencyMs > 0L) {
                        val sec = String.format(Locale.US, "%.1f", message.latencyMs / 1000.0)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${sec}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLastModelMessage) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "تولید مجدد پاسخ",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "کپی متن کامل",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            MarkdownMessageView(
                text = message.text,
                textColor = MaterialTheme.colorScheme.onBackground,
                onCodeCopied = onCodeCopied
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun BorderlessStreamingMessage(text: String, isRetrying: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isRetrying) "در حال تنظیم ارتباط..." else "نوا هوش در حال نوشتن...",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (text.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            MarkdownMessageView(
                text = "$text ▍",
                textColor = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun HistoryConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${conversation.messageCount} پیام",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (conversation.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "سنجاق کردن",
                    tint = if (conversation.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRename,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "تغییر نام"
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "حذف گفتگو",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
