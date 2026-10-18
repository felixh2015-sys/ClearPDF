package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.ExtractTextViewModel
import com.chethan616.clearpdf.ui.viewmodel.ExportFormat
import com.chethan616.clearpdf.ui.theme.BrandColors
import com.chethan616.clearpdf.util.ShareToCloud
import android.net.Uri
import android.widget.Toast
import com.kyant.backdrop.backdrops.LayerBackdrop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

@Composable
fun ExtractTextScreen(
    backdrop: LayerBackdrop,
    viewModel: ExtractTextViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uiSensor = rememberUISensor()
    val isDark = LocalIsDarkMode.current
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF222222)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF777777)
    val accent = BrandColors.Primary

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onSelectFile(context, it) }
    }

    var isVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { isVisible = true }
    val density = LocalDensity.current.density

    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "extractTopBarAlpha"
    )

    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "extractContentAlpha"
    )
    val contentOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "extractContentOffsetY"
    )

    GlassScreenScaffold(
        backdrop = backdrop,
        contentBottomPadding = 16.dp,
        header = { headerBackdrop ->
            // Fade only — the header is glass, and translating glass re-runs its blur+lens.
            GlassScreenHeaderRow(
                title = stringResource(R.string.tool_extract),
                backdrop = headerBackdrop,
                onBack = onBack,
                modifier = Modifier.graphicsLayer { alpha = topBarAlpha }
            )
        }
    ) { contentPadding ->
        // The body fills rather than scrolls (the extracted text has its own scroller), so the
        // header clearance is real padding here, not a scrolled inset.
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffsetY * density
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidButton(
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    backdrop = backdrop,
                    tint = accent,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                    BasicText(stringResource(R.string.viewer_pick_pdf), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                }
                if (state.text.isNotEmpty()) {
                    LiquidButton(onClick = { clipboard.setText(AnnotatedString(state.text)) }, backdrop = backdrop) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp), text)
                        BasicText(stringResource(R.string.copy), style = TextStyle(text, 14.sp, FontWeight.Medium))
                    }
                    com.chethan616.clearpdf.ui.components.LiquidIconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.text)
                            }
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.extract_share_text)))
                        },
                        backdrop = backdrop,
                        tint = LiquidGlassColors.Blue,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.Share, stringResource(R.string.share), Modifier.size(20.dp), Color.White)
                    }
                }
            }

            state.sourceFileName.takeIf { it.isNotBlank() }?.let {
                BasicText(it, style = TextStyle(sub, 12.sp))
            }
            state.errorMessage?.let { BasicText(it, style = TextStyle(Color(0xFFFFB300), 12.sp)) }

            // ── Convert to editable Word / Excel ──
            if (state.hasResult && state.sourceUri != null) {
                BasicText(stringResource(R.string.export_convert_to), style = TextStyle(sub, 12.sp, FontWeight.Medium))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiquidButton(
                        onClick = { viewModel.exportAs(context, ExportFormat.WORD) },
                        backdrop = backdrop,
                        tint = BrandColors.Word,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Description, null, Modifier.size(18.dp), Color.White)
                        BasicText(stringResource(R.string.export_word), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                    }
                    LiquidButton(
                        onClick = { viewModel.exportAs(context, ExportFormat.EXCEL) },
                        backdrop = backdrop,
                        tint = BrandColors.Excel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.TableChart, null, Modifier.size(18.dp), Color.White)
                        BasicText(stringResource(R.string.export_excel), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                    }
                }
                if (state.isExporting) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        val p = state.exportProgress
                        BasicText(
                            when {
                                p == null -> stringResource(R.string.export_preparing)
                                state.exportUsingOcr -> stringResource(R.string.export_progress_ocr, p.first + 1, p.second)
                                else -> stringResource(R.string.export_progress, p.first + 1, p.second)
                            },
                            style = TextStyle(sub, 12.sp)
                        )
                    }
                }
                val exported = state.exportedUri
                val exportedMime = state.exportedMime
                if (exported != null && exportedMime != null && !state.isExporting) {
                    BasicText(
                        stringResource(R.string.export_saved, state.exportedName.orEmpty(), state.exportedSavedLabel.orEmpty()),
                        style = TextStyle(BrandColors.Success, 12.sp)
                    )
                    FileActionsRow(backdrop, exported, exportedMime, state.exportedName, text)
                }
            }

            if (state.canRecognize && !state.isRecognizing) {
                LiquidButton(
                    onClick = { viewModel.recognizeText(context) },
                    backdrop = backdrop,
                    tint = accent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicText(stringResource(R.string.extract_recognize_text), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                }
            }
            if (state.canMakeSearchable) {
                LiquidButton(
                    onClick = { viewModel.makeSearchablePdf(context) },
                    backdrop = backdrop,
                    tint = BrandColors.Success,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSavingSearchable) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        BasicText(stringResource(R.string.extract_make_searchable), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                    }
                }
            }
            state.searchableOutputUri?.let { searchable ->
                BasicText(
                    stringResource(R.string.extract_searchable_saved, state.searchableSavedLabel.orEmpty()),
                    style = TextStyle(BrandColors.Success, 12.sp)
                )
                FileActionsRow(backdrop, searchable, ShareToCloud.MIME_PDF, null, text)
            }

            Box(Modifier.fillMaxWidth().weight(1f).liquidGlassPanel(backdrop, uiSensor).padding(14.dp)) {
                when {
                    state.isExtracting -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                    }
                    state.isRecognizing -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                            val progress = state.recognizeProgress
                            BasicText(
                                if (progress != null) stringResource(R.string.extract_recognizing, progress.first + 1, progress.second)
                                else stringResource(R.string.extract_recognize_text),
                                style = TextStyle(sub, 12.sp)
                            )
                        }
                    }
                    state.text.isNotEmpty() -> SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        BasicText(state.text, style = TextStyle(text, 14.sp))
                    }
                    state.hasResult -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        BasicText(stringResource(R.string.extract_no_text), style = TextStyle(sub, 13.sp))
                    }
                    else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        BasicText(stringResource(R.string.extract_pick_hint), style = TextStyle(sub, 13.sp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Open / upload to Google Drive / share, for a file the screen just produced. */
@Composable
private fun FileActionsRow(backdrop: LayerBackdrop, uri: Uri, mime: String, name: String?, textColor: Color) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LiquidButton(
            onClick = {
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(view)
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_LONG).show()
                }
            },
            backdrop = backdrop,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp), textColor)
            BasicText(stringResource(R.string.open), style = TextStyle(textColor, 13.sp, FontWeight.Medium))
        }
        LiquidButton(
            onClick = { ShareToCloud.uploadToDrive(context, uri, mime, name) },
            backdrop = backdrop,
            tint = BrandColors.Primary,
            modifier = Modifier.weight(1.3f)
        ) {
            Icon(Icons.Rounded.CloudUpload, null, Modifier.size(16.dp), Color.White)
            BasicText(stringResource(R.string.upload_to_drive_short), style = TextStyle(Color.White, 13.sp, FontWeight.Medium))
        }
        LiquidIconButton(
            onClick = { ShareToCloud.share(context, uri, mime, name) },
            backdrop = backdrop,
            tint = BrandColors.Accent,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(Icons.Rounded.Share, stringResource(R.string.share), Modifier.size(20.dp), Color.White)
        }
    }
}
