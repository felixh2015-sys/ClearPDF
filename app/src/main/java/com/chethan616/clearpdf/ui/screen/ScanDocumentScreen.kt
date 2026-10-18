package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.model.ScanFilter
import com.chethan616.clearpdf.ui.components.LiquidGlassErrorCard
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.StarPromptEventBus
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.ScanViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ScanDocumentScreen(
    backdrop: LayerBackdrop,
    viewModel: ScanViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF4CAF50)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    var ocrProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val density = LocalDensity.current.density

    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "scanTopBarAlpha"
    )
    val topBarOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 16f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "scanTopBarOffsetY"
    )

    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "scanContentAlpha"
    )
    val contentOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "scanContentOffsetY"
    )

    // ── Document scanner (Google's in play / foss builds, the camera app in F-Droid) ──
    val startScanner = com.chethan616.clearpdf.scan.rememberDocumentScanner(
        onPages = { uris -> viewModel.onScanComplete(uris, ScanFilter.ORIGINAL) },
        onFinished = { viewModel.cancelScanning() },
        onError = { viewModel.setError(context.getString(R.string.scanner_start_failed)) }
    )

    // Gallery picker (multiple images)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onScanComplete(uris, ScanFilter.AUTO)
        }
        viewModel.cancelScanning()
    }

    fun launchDocumentScanner() {
        viewModel.startScanning()
        startScanner()
    }

    GlassScreenScaffold(
        backdrop = backdrop,
        contentBottomPadding = 16.dp,
        header = { headerBackdrop ->
            // No back button here, so the pill centres against the full width. Fade only — the pill
            // is glass, and translating glass re-runs its blur+lens.
            GlassScreenHeaderRow(
                title = stringResource(R.string.scan_title),
                backdrop = headerBackdrop,
                onBack = null,
                modifier = Modifier.graphicsLayer { alpha = topBarAlpha }
            )
        }
    ) { contentPadding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffsetY * density
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

        // Error display
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            state.error?.let { error ->
                // Glass error card (was a solid pink banner).
                LiquidGlassErrorCard(
                    message = error,
                    backdrop = backdrop,
                    uiSensor = uiSensor,
                    onDismiss = { viewModel.setError(null) }
                )
            }
        }

        if (state.scannedPages.isEmpty()) {
            // ── Empty state ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Rounded.DocumentScanner,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        stringResource(R.string.scan_title),
                        style = TextStyle(text, 20.sp, FontWeight.Bold, textAlign = TextAlign.Center)
                    )
                    BasicText(
                        stringResource(R.string.scan_description),
                        style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center)
                    )
                    Spacer(Modifier.height(8.dp))

                    // Scan button (ML Kit Document Scanner)
                    LiquidButton(
                        onClick = { launchDocumentScanner() },
                        backdrop = backdrop,
                        tint = accent,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, null, Modifier.size(20.dp), Color.White)
                            BasicText(stringResource(R.string.scan_document), style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Gallery button
                    LiquidButton(
                        onClick = {
                            viewModel.startScanning()
                            galleryLauncher.launch("image/*")
                        },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(20.dp), text)
                            BasicText(stringResource(R.string.import_gallery), style = TextStyle(text, 16.sp, FontWeight.SemiBold))
                        }
                    }
                }

                // Features
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BasicText(stringResource(R.string.scan_features), style = TextStyle(text, 16.sp, FontWeight.Bold))

                    val features = listOf(
                        Icons.Rounded.CropFree to stringResource(R.string.scan_feature_edges),
                        Icons.Rounded.CameraAlt to stringResource(R.string.scan_feature_perspective),
                        Icons.Rounded.DocumentScanner to stringResource(R.string.scan_feature_multi_page),
                        Icons.Rounded.PhotoLibrary to stringResource(R.string.scan_feature_gallery),
                        Icons.Rounded.PictureAsPdf to stringResource(R.string.scan_feature_export_pdf)
                    )

                    features.forEach { (icon, feature) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null, Modifier.size(20.dp), accent)
                            BasicText(feature, style = TextStyle(text, 14.sp))
                        }
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        } else {
            // ── Pages view ──
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Page count header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        if (state.scannedPages.size == 1) stringResource(R.string.scan_page_scanned) else stringResource(R.string.scan_pages_scanned, state.scannedPages.size),
                        style = TextStyle(text, 16.sp, FontWeight.SemiBold)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Scan more pages
                        Icon(
                            Icons.Rounded.DocumentScanner, stringResource(R.string.scan_more),
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f))
                                .padding(4.dp)
                                .clickable { launchDocumentScanner() },
                            accent
                        )
                        Icon(
                            Icons.Rounded.PhotoLibrary, stringResource(R.string.add_from_gallery),
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f))
                                .padding(4.dp)
                                .clickable {
                                    viewModel.startScanning()
                                    galleryLauncher.launch("image/*")
                                },
                            accent
                        )
                        Icon(
                            Icons.Rounded.Delete, stringResource(R.string.clear_all),
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                                .padding(4.dp)
                                .clickable { viewModel.clearAllPages() },
                            Color(0xFFFF5252)
                        )
                    }
                }

                // Pages grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(state.scannedPages) { index, page ->
                        Box(
                            Modifier
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    if (isLight) Color(0xFFE0E0E0) else Color(0xFF444444),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(page.uri),
                                contentDescription = stringResource(R.string.page_number, index + 1),
                                contentScale = ContentScale.Crop,
                                colorFilter = getComposeColorFilter(page.filter),
                                modifier = Modifier.fillMaxSize()
                            )

                            // Page number badge
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    "${index + 1}",
                                    style = TextStyle(Color.White, 11.sp, FontWeight.Bold)
                                )
                            }

                            // Delete button
                            Icon(
                                Icons.Rounded.Close, stringResource(R.string.remove_page),
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC000000))
                                    .padding(4.dp)
                                    .clickable { viewModel.deletePage(index) },
                                Color.White
                            )
                        }
                    }
                }

                // Filter bar
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicText(stringResource(R.string.filter), style = TextStyle(text, 14.sp, FontWeight.SemiBold))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            ScanFilter.ORIGINAL to stringResource(R.string.scan_original),
                            ScanFilter.AUTO to stringResource(R.string.scan_auto),
                            ScanFilter.GRAYSCALE to stringResource(R.string.scan_grayscale),
                            ScanFilter.BLACK_WHITE to stringResource(R.string.scan_black_white),
                            ScanFilter.COLOR to stringResource(R.string.scan_vivid)
                        )
                        filters.forEach { (filter, label) ->
                            val currentFilter = state.scannedPages.firstOrNull()?.filter ?: ScanFilter.ORIGINAL
                            val isSelected = currentFilter == filter
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accent else accent.copy(0.12f))
                                    .clickable {
                                        state.scannedPages.indices.forEach { i ->
                                            viewModel.applyFilter(i, filter)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                BasicText(
                                    label,
                                    style = TextStyle(
                                        if (isSelected) Color.White else text,
                                        13.sp, FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Save as PDF button
            LiquidButton(
                onClick = {
                    scope.launch {
                        viewModel.startScanning() // reuse as "saving" indicator
                        val filter = state.scannedPages.firstOrNull()?.filter ?: ScanFilter.ORIGINAL
                        val pdfUri = savePagesAsPdf(
                            context,
                            state.scannedPages.map { it.uri },
                            filter,
                            runOcr = true,
                            onProgress = { done, total -> ocrProgress = done to total }
                        )
                        ocrProgress = null
                        viewModel.cancelScanning()
                        if (pdfUri != null) {
                            viewModel.markAsSaved(pdfUri)
                            if (GitHubStarPromptManager.recordSuccess(context)) {
                                StarPromptEventBus.requestPrompt()
                            }
                            Toast.makeText(context, context.getString(R.string.pdf_saved_successfully), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.setError(context.getString(R.string.pdf_save_failed))
                        }
                    }
                },
                backdrop = backdrop,
                tint = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 14.dp)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(22.dp), Color.White)
                    BasicText(
                        when {
                            ocrProgress != null -> stringResource(R.string.scan_ocr_progress, ocrProgress!!.first + 1, ocrProgress!!.second)
                            state.isScanning -> stringResource(R.string.saving)
                            else -> stringResource(R.string.save_as_pdf)
                        },
                        style = TextStyle(Color.White, 16.sp, FontWeight.Bold)
                    )
                }
            }

            // After saving: upload straight to Google Drive or share.
            state.savedPdfUri?.let { saved ->
                BasicText(
                    stringResource(R.string.scan_saved_with_ocr),
                    style = TextStyle(sub, 12.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidButton(
                        onClick = { com.chethan616.clearpdf.util.ShareToCloud.uploadToDrive(context, saved, com.chethan616.clearpdf.util.ShareToCloud.MIME_PDF) },
                        backdrop = backdrop,
                        tint = accent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp), Color.White)
                        BasicText(stringResource(R.string.upload_to_drive), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                    }
                    LiquidButton(
                        onClick = { com.chethan616.clearpdf.util.ShareToCloud.share(context, saved, com.chethan616.clearpdf.util.ShareToCloud.MIME_PDF) },
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp), text)
                        BasicText(stringResource(R.string.share), style = TextStyle(text, 14.sp, FontWeight.Medium))
                    }
                }
            }
        }
        }
    }
    }
}

/**
 * Converts scanned page images to a PDF with the filter applied, then runs on-device OCR
 * (Spanish + English) on every page and bakes the result in as an invisible text layer.
 * The saved PDF looks exactly like the scan but its text can be searched, copied and read
 * by AI assistants. If OCR fails, the plain image PDF is still saved.
 */
private suspend fun savePagesAsPdf(
    context: Context,
    uris: List<Uri>,
    filter: ScanFilter,
    runOcr: Boolean = true,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
): Uri? {
    return withContext(Dispatchers.IO) {
        var imagePdf: File? = null
        try {
            val pdfDocument = PdfDocument()
            val wordsByPage = mutableMapOf<Int, List<com.kyant.pdfcore.searchable.InvisibleWord>>()
            var pageIndex = 0

            uris.forEach { uri ->
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@forEach
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap != null) {
                    val bitmap = applyBitmapFilter(originalBitmap, filter)
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width, bitmap.height, pageIndex + 1
                    ).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)

                    if (runOcr) {
                        withContext(Dispatchers.Main) { onProgress(pageIndex, uris.size) }
                        val words = runCatching {
                            com.chethan616.clearpdf.data.repository.PdfServiceLocator.ocrService
                                .recognize(context, bitmap).words
                        }.getOrNull().orEmpty()
                        if (words.isNotEmpty()) {
                            wordsByPage[pageIndex] = words.map {
                                com.kyant.pdfcore.searchable.InvisibleWord(it.text, it.left, it.top, it.right, it.bottom)
                            }
                        }
                    }

                    if (bitmap !== originalBitmap) bitmap.recycle()
                    originalBitmap.recycle()
                    pageIndex++
                }
            }
            if (pageIndex == 0) {
                pdfDocument.close()
                return@withContext null
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Escaneo_$timeStamp.pdf"

            // The image-only PDF goes to the cache first; the text layer is stamped onto a copy.
            val tmp = File(context.cacheDir, "scan_$timeStamp.pdf")
            imagePdf = tmp
            tmp.outputStream().use {
                pdfDocument.writeTo(it)
                it.flush()
            }
            pdfDocument.close()

            val pdfUri = createScanOutputUri(context, fileName)
            val stamped = wordsByPage.isNotEmpty() && runCatching {
                com.kyant.pdfcore.searchable.PdfSearchableStamper.stamp(
                    context, Uri.fromFile(tmp), pdfUri, wordsByPage
                )
            }.isSuccess
            if (!stamped) {
                val outputStream = context.contentResolver.openOutputStream(pdfUri, "wt")
                    ?: throw IllegalStateException("Unable to open scan output stream")
                outputStream.use { out -> tmp.inputStream().use { it.copyTo(out) } }
            }
            pdfUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            imagePdf?.delete()
        }
    }
}

private fun createScanOutputUri(context: Context, fileName: String): Uri {
    val customUri = SaveLocationManager.getSaveUri(context)
    if (customUri != null) {
        try {
            val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, customUri)
            val created = tree?.createFile("application/pdf", fileName)?.uri
            if (created != null) {
                return created
            }
        } catch (_: Exception) {
            // Fall back to default Downloads location.
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Unable to create scan output in Downloads")
    }

    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, fileName)
    if (!file.exists()) file.createNewFile()
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

/**
 * Apply a ScanFilter to a Bitmap using ColorMatrix
 */
private fun applyBitmapFilter(src: Bitmap, filter: ScanFilter): Bitmap {
    if (filter == ScanFilter.ORIGINAL) return src

    val cm = when (filter) {
        ScanFilter.GRAYSCALE -> {
            ColorMatrix().apply { setSaturation(0f) }
        }
        ScanFilter.BLACK_WHITE -> {
            // High-contrast B&W: desaturate + boost contrast
            val gray = ColorMatrix().apply { setSaturation(0f) }
            val contrast = ColorMatrix(floatArrayOf(
                2f, 0f, 0f, 0f, -180f,
                0f, 2f, 0f, 0f, -180f,
                0f, 0f, 2f, 0f, -180f,
                0f, 0f, 0f, 1f, 0f
            ))
            contrast.preConcat(gray)
            contrast
        }
        ScanFilter.AUTO -> {
            // Auto enhance: slight brightness + contrast boost
            ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.2f, 0f, 0f, 10f,
                0f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        ScanFilter.COLOR -> {
            // Vivid: boost saturation and slight contrast
            val sat = ColorMatrix().apply { setSaturation(1.5f) }
            val bright = ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 5f,
                0f, 1.1f, 0f, 0f, 5f,
                0f, 0f, 1.1f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            ))
            bright.preConcat(sat)
            bright
        }
        else -> return src
    }

    val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return output
}

/**
 * Get Compose ColorFilter from ScanFilter
 */
private fun getComposeColorFilter(filter: ScanFilter): ColorFilter? {
    return when (filter) {
        ScanFilter.ORIGINAL -> null
        ScanFilter.GRAYSCALE -> ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
        )
        ScanFilter.BLACK_WHITE -> {
            val m = androidx.compose.ui.graphics.ColorMatrix()
            m.setToSaturation(0f)
            val contrast = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                2f, 0f, 0f, 0f, -180f,
                0f, 2f, 0f, 0f, -180f,
                0f, 0f, 2f, 0f, -180f,
                0f, 0f, 0f, 1f, 0f
            ))
            contrast.timesAssign(m)
            ColorFilter.colorMatrix(contrast)
        }
        ScanFilter.AUTO -> ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.2f, 0f, 0f, 10f,
                0f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
        )
        ScanFilter.COLOR -> {
            val m = androidx.compose.ui.graphics.ColorMatrix()
            m.setToSaturation(1.5f)
            val bright = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 5f,
                0f, 1.1f, 0f, 0f, 5f,
                0f, 0f, 1.1f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            ))
            bright.timesAssign(m)
            ColorFilter.colorMatrix(bright)
        }
    }
}
