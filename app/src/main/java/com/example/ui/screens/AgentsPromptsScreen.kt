package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiChatModel
import com.example.data.model.CustomAssistant
import com.example.data.model.SavedPrompt
import com.example.data.model.UiState
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.AgentsPromptsViewModel
import java.util.Locale

private val PromptCategories = listOf("همه", "برنامه‌نویسی", "محتوا", "تحلیل", "تصویر", "عمومی")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsPromptsScreen(
    viewModel: AgentsPromptsViewModel,
    onRunAssistant: (CustomAssistant) -> Unit,
    onRunPrompt: (String) -> Unit
) {
    val assistantsState by viewModel.assistantsState.collectAsStateWithLifecycle()
    val savedPromptsState by viewModel.savedPromptsState.collectAsStateWithLifecycle()
    val noticeMessage by viewModel.noticeMessage.collectAsStateWithLifecycle()
    val isOptimizing by viewModel.isOptimizing.collectAsStateWithLifecycle()
    val optimizedResult by viewModel.optimizedResult.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var showAssistantDialog by remember { mutableStateOf(false) }
    var editingAssistant by remember { mutableStateOf<CustomAssistant?>(null) }
    var assistantPendingDelete by remember { mutableStateOf<CustomAssistant?>(null) }

    var showPromptDialog by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<SavedPrompt?>(null) }
    var prefilledPromptContent by remember { mutableStateOf("") }
    var promptPendingDelete by remember { mutableStateOf<SavedPrompt?>(null) }

    LaunchedEffect(noticeMessage) {
        val msg = noticeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearNotice()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 880.dp)
                    .fillMaxSize()
            ) {
                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "مرکز دستیارها و مهندسی پرامپت",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ساخت دستیارهای تخصصی با دستورالعمل اختصاصی، کتابخانه ابری پرامپت‌ها و بهینه‌ساز واقعی Gemini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("دستیارهای سفارشی") },
                        icon = { Icon(Icons.Filled.SmartToy, contentDescription = null) },
                        modifier = Modifier.testTag("tab_custom_assistants")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("پرامپت و بهینه‌ساز") },
                        icon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null) },
                        modifier = Modifier.testTag("tab_prompt_hub")
                    )
                }

                if (selectedTab == 0) {
                    CustomAssistantsTabContent(
                        assistantsState = assistantsState,
                        onCreateNew = {
                            editingAssistant = null
                            showAssistantDialog = true
                        },
                        onEdit = { assistant ->
                            editingAssistant = assistant
                            showAssistantDialog = true
                        },
                        onDeleteRequest = { assistant ->
                            assistantPendingDelete = assistant
                        },
                        onRunAssistant = onRunAssistant
                    )
                } else {
                    PromptsAndOptimizerTabContent(
                        savedPromptsState = savedPromptsState,
                        isOptimizing = isOptimizing,
                        optimizedResult = optimizedResult,
                        onOptimize = { raw, target -> viewModel.optimizePrompt(raw, target) },
                        onClearOptimized = { viewModel.clearOptimizedResult() },
                        onSaveOptimizedAsTemplate = { improved ->
                            editingPrompt = null
                            prefilledPromptContent = improved
                            showPromptDialog = true
                        },
                        onCreatePrompt = {
                            editingPrompt = null
                            prefilledPromptContent = ""
                            showPromptDialog = true
                        },
                        onEditPrompt = { prompt ->
                            editingPrompt = prompt
                            prefilledPromptContent = prompt.content
                            showPromptDialog = true
                        },
                        onDeletePromptRequest = { prompt ->
                            promptPendingDelete = prompt
                        },
                        onRunPrompt = onRunPrompt
                    )
                }
            }
        }
    }

    if (showAssistantDialog) {
        AssistantEditorDialog(
            initial = editingAssistant,
            onDismiss = { showAssistantDialog = false },
            onSave = { name, role, prompt, modelId, temp ->
                viewModel.saveAssistant(
                    existingId = editingAssistant?.id,
                    name = name,
                    roleDescription = role,
                    systemPrompt = prompt,
                    modelName = modelId,
                    temperature = temp.toDouble()
                )
                showAssistantDialog = false
            }
        )
    }

    assistantPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { assistantPendingDelete = null },
            title = { Text("حذف دستیار اختصاصی") },
            text = { Text("آیا از حذف دستیار «${target.name}» از فضای ابری اطمینان دارید؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAssistant(target.id)
                        assistantPendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_assistant_btn")
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { assistantPendingDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    if (showPromptDialog) {
        PromptEditorDialog(
            initial = editingPrompt,
            prefilledContent = prefilledPromptContent,
            onDismiss = { showPromptDialog = false },
            onSave = { title, category, content ->
                viewModel.savePromptTemplate(
                    existingId = editingPrompt?.id,
                    title = title,
                    category = category,
                    content = content
                )
                showPromptDialog = false
            }
        )
    }

    promptPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { promptPendingDelete = null },
            title = { Text("حذف پرامپت") },
            text = { Text("آیا پرامپت «${target.title}» حذف شود؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSavedPrompt(target.id)
                        promptPendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_prompt_btn")
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { promptPendingDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun CustomAssistantsTabContent(
    assistantsState: UiState<List<CustomAssistant>>,
    onCreateNew: () -> Unit,
    onEdit: (CustomAssistant) -> Unit,
    onDeleteRequest: (CustomAssistant) -> Unit,
    onRunAssistant: (CustomAssistant) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "دستیارهای ذخیره‌شده شما",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onCreateNew,
                    modifier = Modifier.testTag("create_assistant_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ساخت دستیار جدید")
                }
            }
        }

        when (assistantsState) {
            UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = assistantsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val assistants = assistantsState.data
                if (assistants.isEmpty()) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SmartToy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "هنوز دستیار اختصاصی نساخته‌اید",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "می‌توانید دستیارهایی با نقش دقیق (مثلاً ممیز امنیت، معمار کاتلین یا ویراستار حقوقی) و تنظیمات مدل اختصاصی بسازید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                FilledTonalButton(onClick = onCreateNew) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ایجاد اولین دستیار")
                                }
                            }
                        }
                    }
                } else {
                    items(assistants, key = { it.id }) { assistant ->
                        CustomAssistantCard(
                            assistant = assistant,
                            onRun = { onRunAssistant(assistant) },
                            onEdit = { onEdit(assistant) },
                            onDelete = { onDeleteRequest(assistant) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomAssistantCard(
    assistant: CustomAssistant,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val modelInfo = AiChatModel.fromId(assistant.modelName)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("assistant_card_${assistant.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assistant.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = assistant.roleDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "ویرایش دستیار",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "حذف دستیار",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = modelInfo.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = String.format(Locale.US, "Temp: %.1f", assistant.temperature),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "${assistant.usageCount} اجرا",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = assistant.systemPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRun,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("run_assistant_${assistant.id}")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("شروع گفتگو با این دستیار")
            }
        }
    }
}

@Composable
private fun PromptsAndOptimizerTabContent(
    savedPromptsState: UiState<List<SavedPrompt>>,
    isOptimizing: Boolean,
    optimizedResult: com.example.data.model.PromptOptimizationResult?,
    onOptimize: (String, String) -> Unit,
    onClearOptimized: () -> Unit,
    onSaveOptimizedAsTemplate: (String) -> Unit,
    onCreatePrompt: () -> Unit,
    onEditPrompt: (SavedPrompt) -> Unit,
    onDeletePromptRequest: (SavedPrompt) -> Unit,
    onRunPrompt: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var rawPromptInput by rememberSaveable { mutableStateOf("") }
    var targetType by rememberSaveable { mutableStateOf("chat") }
    var selectedCategory by rememberSaveable { mutableStateOf("همه") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live AI Prompt Optimizer Card
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "بهینه‌ساز مهندسی پرامپت (Gemini)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ایده خام خود را بنویسید تا هوش مصنوعی ساختار حرفه‌ای با خروجی JSON تولید کند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = rawPromptInput,
                        onValueChange = { rawPromptInput = it },
                        label = { Text("پرامپت خام یا شرح نیاز") },
                        placeholder = { Text("مثلاً: یک معماری میکروسرویس برای درگاه پرداخت طراحی کن") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("optimizer_input_field")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = targetType == "chat",
                            onClick = { targetType = "chat" },
                            label = { Text("چت / کدنویسی") }
                        )
                        FilterChip(
                            selected = targetType == "image",
                            onClick = { targetType = "image" },
                            label = { Text("استودیو تصویر") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { onOptimize(rawPromptInput, targetType) },
                        enabled = !isOptimizing && rawPromptInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("optimizer_run_btn")
                    ) {
                        if (isOptimizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("در حال مهندسی پرامپت...")
                        } else {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بهینه‌سازی با Gemini")
                        }
                    }

                    if (optimizedResult != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "پرامپت بهینه‌شده",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${optimizedResult.totalTokens} توکن · ${optimizedResult.latencyMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = optimizedResult.improvedPrompt,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (optimizedResult.rationale.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "تحلیل: ${optimizedResult.rationale}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(optimizedResult.improvedPrompt))
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("کپی")
                                    }
                                    FilledTonalButton(
                                        onClick = { onSaveOptimizedAsTemplate(optimizedResult.improvedPrompt) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ذخیره")
                                    }
                                    Button(
                                        onClick = {
                                            val text = optimizedResult.improvedPrompt
                                            onClearOptimized()
                                            onRunPrompt(text)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("اجرا")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saved Prompts Library Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Bookmarks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "کتابخانه پرامپت‌های ذخیره‌شده",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                FilledTonalButton(
                    onClick = onCreatePrompt,
                    modifier = Modifier.testTag("create_prompt_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("پرامپت جدید")
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PromptCategories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }
        }

        when (savedPromptsState) {
            UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = savedPromptsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val filtered = savedPromptsState.data.filter {
                    selectedCategory == "همه" || it.category == selectedCategory
                }
                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "هیچ پرامپتی در این دسته ثبت نشده است",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "پرامپت‌های پرکاربرد خود را ذخیره کنید تا با یک کلیک در چت اجرا شوند.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { prompt ->
                        SavedPromptCard(
                            prompt = prompt,
                            onRun = { onRunPrompt(prompt.content) },
                            onCopy = { clipboard.setText(AnnotatedString(prompt.content)) },
                            onEdit = { onEditPrompt(prompt) },
                            onDelete = { onDeletePromptRequest(prompt) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedPromptCard(
    prompt: SavedPrompt,
    onRun: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_prompt_card_${prompt.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = prompt.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = prompt.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Row {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "کپی پرامپت", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "ویرایش پرامپت", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "حذف پرامپت",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = prompt.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onRun,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("run_prompt_${prompt.id}")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("اجرا در چت هوشمند")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantEditorDialog(
    initial: CustomAssistant?,
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, prompt: String, modelId: String, temp: Float) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var role by remember { mutableStateOf(initial?.roleDescription ?: "") }
    var prompt by remember { mutableStateOf(initial?.systemPrompt ?: "") }
    var selectedModel by remember { mutableStateOf(AiChatModel.fromId(initial?.modelName)) }
    var temp by remember { mutableFloatStateOf((initial?.temperature ?: 0.7).toFloat().coerceIn(0f, 1.5f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "ساخت دستیار اختصاصی" else "ویرایش دستیار") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("نام دستیار (حداکثر ۶۰ کاراکتر)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assistant_name_input")
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { if (it.length <= 160) role = it },
                    label = { Text("شرح نقش") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assistant_role_input")
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { if (it.length <= 2000) prompt = it },
                    label = { Text("دستورالعمل سیستمی (System Prompt)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assistant_prompt_input")
                )
                Text(
                    text = "مدل پردازشگر",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiChatModel.entries.forEach { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { selectedModel = model },
                            label = { Text(model.badge) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "دمای خلاقیت (Temperature)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", temp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = temp,
                    onValueChange = { temp = it },
                    valueRange = 0f..1.5f,
                    steps = 14
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, role, prompt, selectedModel.modelId, temp) },
                enabled = name.isNotBlank() && role.isNotBlank() && prompt.isNotBlank(),
                modifier = Modifier.testTag("save_assistant_confirm_btn")
            ) {
                Text("ذخیره ابری")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptEditorDialog(
    initial: SavedPrompt?,
    prefilledContent: String,
    onDismiss: () -> Unit,
    onSave: (title: String, category: String, content: String) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "عمومی") }
    var content by remember { mutableStateOf(initial?.content ?: prefilledContent) }

    val availableCategories = PromptCategories.filter { it != "همه" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "ذخیره در کتابخانه پرامپت" else "ویرایش پرامپت") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 80) title = it },
                    label = { Text("عنوان پرامپت") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prompt_title_input")
                )
                Text(
                    text = "دسته‌بندی",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 2000) content = it },
                    label = { Text("متن پرامپت") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prompt_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, category, content) },
                enabled = title.isNotBlank() && content.isNotBlank(),
                modifier = Modifier.testTag("save_prompt_confirm_btn")
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
