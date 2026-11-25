package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AiImageModel
import com.example.data.model.ImageAspectOption
import com.example.data.model.ImageCreation
import com.example.data.model.UiState
import com.example.ui.viewmodel.ImageGenUiState
import com.example.ui.viewmodel.ImageStudioViewModel

@Composable
fun ImageStudioScreen(
    viewModel: ImageStudioViewModel
) {
    val context = LocalContext.current
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.selectedStyle.collectAsStateWithLifecycle()
    val selectedAspect by viewModel.selectedAspect.collectAsStateWithLifecycle()
    val sourceBitmap by viewModel.sourceBitmap.collectAsStateWithLifecycle()
    val genState by viewModel.generationState.collectAsStateWithLifecycle()
    val creationsState by viewModel.creationsState.collectAsStateWithLifecycle()
    val isOptimizing by viewModel.isOptimizingPrompt.collectAsStateWithLifecycle()
    val noticeMessage by viewModel.noticeMessage.collectAsStateWithLifecycle()

    var promptText by rememberSaveable { mutableStateOf("") }
    var negativePromptText by rememberSaveable { mutableStateOf("") }

    val referencePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachReferenceImage(context, uri)
        }
    }

    if (noticeMessage != null) {
        LaunchedEffect(noticeMessage) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearNotice()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "استودیو تصویر هوش مصنوعی",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تولید، ویرایش و بهینه‌سازی پرامپت تصویری با Nano Banana",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            if (noticeMessage != null) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = noticeMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                StudioPreviewCanvas(
                    state = genState,
                    onSaveToGallery = { bitmap, title ->
                        viewModel.exportToGallery(context, bitmap, title)
                    },
                    onShareImage = { bitmap, title ->
                        viewModel.shareImage(context, bitmap, title)
                    }
                )
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "توصیف تصویر (پرامپت)",
                                style = MaterialTheme.typography.labelLarge
                            )
                            TextButton(
                                onClick = {
                                    viewModel.optimizePrompt(promptText) { improved ->
                                        promptText = improved
                                    }
                                },
                                enabled = !isOptimizing && promptText.isNotBlank()
                            ) {
                                if (isOptimizing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("در حال ارتقا...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("بهینه‌سازی با AI")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            placeholder = {
                                Text(stringResource(R.string.studio_prompt_placeholder))
                            },
                            minLines = 2,
                            maxLines = 5,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_prompt_input")
                        )

                        OutlinedTextField(
                            value = negativePromptText,
                            onValueChange = { negativePromptText = it },
                            label = { Text("عناصر ناخواسته / Negative Prompt (اختیاری)") },
                            placeholder = { Text("مثلاً: متن اضافه، تاری، نور تند") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (sourceBitmap != null) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = sourceBitmap!!.asImageBitmap(),
                                        contentDescription = "تصویر مرجع برای ویرایش",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "حالت ویرایش تصویر فعال است",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "دستور تغییر مورد نظر خود را در کادر بالا بنویسید.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.clearReferenceImage() }) {
                                        Icon(Icons.Filled.Close, contentDescription = "حذف تصویر مرجع")
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    referencePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("studio_pick_ref_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ویرایش روی عکس موجود (Image-to-Image)")
                            }
                        }

                        Text(
                            text = "مدل رندر تصویر",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiImageModel.entries.forEach { model ->
                                FilterChip(
                                    selected = selectedModel == model,
                                    onClick = { viewModel.selectModel(model) },
                                    label = { Text(model.titleFa) }
                                )
                            }
                        }

                        Text(
                            text = "سبک هنری",
                            style = MaterialTheme.typography.labelLarge
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(viewModel.stylePresets) { style ->
                                FilterChip(
                                    selected = selectedStyle == style,
                                    onClick = { viewModel.selectStyle(style) },
                                    label = { Text(style) }
                                )
                            }
                        }

                        Text(
                            text = "نسبت ابعاد",
                            style = MaterialTheme.typography.labelLarge
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ImageAspectOption.entries) { ratio ->
                                FilterChip(
                                    selected = selectedAspect == ratio,
                                    onClick = { viewModel.selectAspect(ratio) },
                                    label = { Text(ratio.label) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.generateImage(context, promptText, negativePromptText)
                            },
                            enabled = genState !is ImageGenUiState.Loading && promptText.isNotBlank(),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("studio_generate_btn")
                        ) {
                            if (genState is ImageGenUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("در حال خلق تصویر...")
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (sourceBitmap != null) "اعمال ویرایش با AI" else "تولید تصویر با AI",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "گالری و تاریخچه پرامپت‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            when (val state = creationsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp))
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item {
                            Text(
                                text = "هنوز تصویری نساخته‌اید. اولین ایده خود را در بالا بنویسید!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(state.data, key = { it.id }) { item ->
                            ImageHistoryItem(
                                creation = item,
                                onReusePrompt = {
                                    promptText = item.prompt
                                    negativePromptText = item.negativePrompt.orEmpty()
                                    viewModel.loadCreationPreview(context, item)
                                },
                                onDelete = { viewModel.deleteCreation(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioPreviewCanvas(
    state: ImageGenUiState,
    onSaveToGallery: (android.graphics.Bitmap, String) -> Unit,
    onShareImage: (android.graphics.Bitmap, String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        when (state) {
            is ImageGenUiState.Idle -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_empty),
                        contentDescription = "بوم آماده خلق تصویر",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xD9060913))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "بوم رندر آماده است",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "پرامپت خود را بنویسید یا با هوش مصنوعی ارتقا دهید",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
            is ImageGenUiState.Loading -> {
                val infinite = rememberInfiniteTransition(label = "shimmer")
                val alpha by infinite.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 0.75f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1100, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha * 0.45f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "در حال رندر تصویر توسط Nano Banana...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تولید تصویر با کیفیت بالا ممکن است ۳۰ تا ۶۰ ثانیه زمان ببرد",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is ImageGenUiState.Success -> {
                Column(modifier = Modifier.padding(14.dp)) {
                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = state.prompt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                (state.bitmap.width.toFloat() / state.bitmap.height.coerceAtLeast(1))
                                    .coerceIn(0.55f, 1.85f)
                            )
                            .clip(MaterialTheme.shapes.large)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.modelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (state.latencyMs > 0L) {
                            Text(
                                text = "زمان رندر: ${String.format(java.util.Locale.US, "%.1f", state.latencyMs / 1000.0)}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onSaveToGallery(state.bitmap, state.prompt) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("studio_save_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ذخیره در گالری")
                        }
                        OutlinedButton(
                            onClick = { onShareImage(state.bitmap, state.prompt) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("studio_share_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اشتراک‌گذاری")
                        }
                    }
                }
            }
            is ImageGenUiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "خطا در تولید تصویر",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageHistoryItem(
    creation: ImageCreation,
    onReusePrompt: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onReusePrompt)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = creation.prompt,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val latencyPart = if (creation.latencyMs > 0L) " • ${creation.latencyMs / 1000}s" else ""
                Text(
                    text = "${creation.stylePreset} • ${creation.aspectRatio}$latencyPart",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "حذف سابقه",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
