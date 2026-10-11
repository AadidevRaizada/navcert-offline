package com.MAD.n152

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.MAD.n152.ui.theme.N152Theme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenCVLoader.initLocal()
        setContent { N152Theme { OcrApp() } }
    }
}

enum class AppDestination(val label: String) {
    SCAN("Scan"),
    DOCUMENTS("Documents"),
    ASSISTANT("Ask"),
    ABOUT("About")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OcrApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val repository = remember { DocumentRepository(context.applicationContext) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val handwritingRecognizer = remember { PaddleHandwritingRecognizer(context.applicationContext) }
    val documentAssistant = remember {
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OfflineDocumentAssistant(context.applicationContext) }
    }

    var destination by rememberSaveable { mutableStateOf(AppDestination.SCAN) }
    var scanStep by rememberSaveable { mutableStateOf(ScanStep.CAPTURE) }
    var recognitionMode by rememberSaveable { mutableStateOf(RecognitionMode.AUTO) }
    var enhancementMode by rememberSaveable { mutableStateOf(EnhancementMode.AUTO) }
    var rotationDegrees by rememberSaveable { mutableStateOf(0) }
    var extractedText by rememberSaveable { mutableStateOf("") }
    var documentTitle by rememberSaveable { mutableStateOf("") }
    var reviewNotes by rememberSaveable { mutableStateOf("") }
    var highlightedPhrases by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var bookmarked by rememberSaveable { mutableStateOf(false) }
    var highlightColor by rememberSaveable { mutableStateOf(0xFFFFD978.toInt()) }
    var activeDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceType by rememberSaveable { mutableStateOf("unknown") }
    var status by rememberSaveable { mutableStateOf("Ready to scan") }
    var isProcessing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var currentImageUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ocrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageQuality by remember { mutableStateOf<ImageQuality?>(null) }
    var documentDetected by remember { mutableStateOf(false) }
    var documentStats by remember { mutableStateOf<DocumentText?>(null) }
    var certificateFields by remember { mutableStateOf<CertificateFields?>(null) }
    var documents by remember { mutableStateOf<List<StoredDocument>>(emptyList()) }
    var folders by remember { mutableStateOf<List<StoredFolder>>(emptyList()) }
    var documentsLoading by remember { mutableStateOf(true) }
    var assistantAnswer by remember { mutableStateOf<DocumentAnswer?>(null) }
    var assistantBusy by remember { mutableStateOf(false) }

    suspend fun refreshDocuments() {
        documentsLoading = true
        documents = withContext(Dispatchers.IO) { repository.listDocuments() }
        folders = withContext(Dispatchers.IO) { repository.listFolders() }
        documentsLoading = false
    }

    LaunchedEffect(Unit) { refreshDocuments() }
    DisposableEffect(recognizer) { onDispose { recognizer.close() } }
    DisposableEffect(handwritingRecognizer) { onDispose { handwritingRecognizer.close() } }
    DisposableEffect(documentAssistant) {
        onDispose { if (documentAssistant.isInitialized()) documentAssistant.value.close() }
    }
    DisposableEffect(previewBitmap) {
        val ownedBitmap = previewBitmap
        onDispose { ownedBitmap?.recycle() }
    }
    DisposableEffect(sourceBitmap) {
        val ownedBitmap = sourceBitmap
        onDispose { ownedBitmap?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    DisposableEffect(ocrBitmap) {
        val ownedBitmap = ocrBitmap
        onDispose { ownedBitmap?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }

    fun navigate(target: AppDestination) {
        destination = target
        scope.launch { drawerState.close() }
    }

    fun newScan() {
        scanStep = ScanStep.CAPTURE
        extractedText = ""
        documentTitle = ""
        reviewNotes = ""
        highlightedPhrases = arrayListOf()
        bookmarked = false
        highlightColor = 0xFFFFD978.toInt()
        activeDocumentId = null
        activeFolderId = null
        sourceType = "unknown"
        documentStats = null
        certificateFields = null
        currentImageUri = null
        sourceBitmap = null
        ocrBitmap = null
        previewBitmap = null
        imageQuality = null
        documentDetected = false
        status = "Ready to scan"
        destination = AppDestination.SCAN
    }

    fun openDocument(document: StoredDocument) {
        destination = AppDestination.SCAN
        scanStep = ScanStep.RESULT
        activeDocumentId = document.id
        activeFolderId = document.folderId
        sourceType = document.sourceType
        recognitionMode = RecognitionMode.entries.firstOrNull {
            it.name.equals(document.recognitionMode, ignoreCase = true)
        } ?: RecognitionMode.AUTO
        documentTitle = document.title
        extractedText = document.text
        reviewNotes = document.reviewNotes
        highlightedPhrases = ArrayList(document.highlightedPhrases)
        bookmarked = document.bookmarked
        highlightColor = document.highlightColor
        documentStats = DocumentText(
            text = document.text,
            blocks = 0,
            lines = document.lineCount,
            words = document.wordCount
        )
        certificateFields = CertificateFieldExtractor.extract(document.text, document.title)
        imageQuality = null
        documentDetected = false
        status = "Stored document opened"
        scope.launch {
            previewBitmap = withContext(Dispatchers.IO) {
                document.previewPath?.let(BitmapFactory::decodeFile)
            }
        }
    }

    fun rebuildPreview(
        source: Bitmap? = sourceBitmap,
        mode: EnhancementMode = enhancementMode,
        rotation: Int = rotationDegrees
    ) {
        val input = source ?: return
        scope.launch {
            isProcessing = true
            activeDocumentId = null
            status = "Improving image"
                documentStats = null
                certificateFields = null
            try {
                val scanResult = withContext(Dispatchers.Default) {
                    val rotated = rotateBitmap(input, rotation)
                    try {
                        DocumentScanner.prepare(
                            rotated,
                            detectDocument = true,
                            enhancementMode = mode
                        )
                    } finally {
                        if (!rotated.isRecycled) rotated.recycle()
                    }
                }
                ocrBitmap = scanResult.bitmap
                previewBitmap = withContext(Dispatchers.Default) {
                    createPreviewBitmap(scanResult.bitmap)
                }
                imageQuality = scanResult.quality
                documentDetected = scanResult.detected
                status = if (scanResult.detected) "Page aligned" else "Preview ready"
            } catch (error: Exception) {
                status = error.localizedMessage ?: "Could not process this image"
            } finally {
                isProcessing = false
            }
        }
    }

    fun loadImage(uri: Uri) {
        scope.launch {
            isProcessing = true
            status = "Opening image"
            try {
                val decoded = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
                    ?: error("Could not open the selected image")
                currentImageUri = uri
                sourceBitmap = decoded
                rotationDegrees = 0
                enhancementMode = EnhancementMode.AUTO
                extractedText = ""
                documentTitle = ""
                reviewNotes = ""
                highlightedPhrases = arrayListOf()
                bookmarked = false
                highlightColor = 0xFFFFD978.toInt()
                scanStep = ScanStep.PREVIEW
                isProcessing = false
                rebuildPreview(source = decoded, mode = EnhancementMode.AUTO, rotation = 0)
            } catch (error: Exception) {
                status = error.localizedMessage ?: "Could not open this image"
                isProcessing = false
            }
        }
    }

    fun extractText() {
        val bitmap = ocrBitmap ?: return
        isProcessing = true
        status = if (recognitionMode == RecognitionMode.HANDWRITING) {
            "Reading handwriting"
        } else {
            "Reading text"
        }
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val document = DocumentTextFormatter.format(result)
                if (recognitionMode == RecognitionMode.HANDWRITING) {
                    scope.launch {
                        try {
                            status = "Recognizing each handwritten line"
                            val handwriting = withContext(Dispatchers.Default) {
                                handwritingRecognizer.recognize(bitmap, result)
                            }
                            extractedText = handwriting.text
                            documentTitle = inferDocumentTitle(handwriting.text)
                            documentStats = DocumentText(
                                text = handwriting.text,
                                blocks = result.textBlocks.size,
                                lines = handwriting.lineCount,
                                words = Regex("\\S+").findAll(handwriting.text).count()
                            )
                            status = when {
                                handwriting.text.isBlank() -> "No readable handwriting found"
                                handwriting.averageConfidence >= 0.55f -> "Handwriting extracted with high confidence"
                                else -> "Handwriting extracted. Review uncertain words"
                            }
                        } catch (error: Exception) {
                            Log.e(
                                "PaddleHandwriting",
                                "Offline handwriting recognition failed; using ML Kit fallback",
                                error,
                            )
                            // Keep the baseline result usable if the handwriting model cannot run.
                            extractedText = document.text
                            documentTitle = inferDocumentTitle(document.text)
                            documentStats = document
                            status = "Handwriting model unavailable. Baseline text shown"
                        } finally {
                            scanStep = ScanStep.RESULT
                            isProcessing = false
                        }
                    }
                    return@addOnSuccessListener
                }

                val finalText = OcrTextCorrector.correct(document.text)
                extractedText = finalText
                documentTitle = inferDocumentTitle(finalText)
                documentStats = document.copy(text = finalText)
                scope.launch {
                    status = "Reading certificate details"
                    certificateFields = withContext(Dispatchers.Default) {
                        runCatching {
                            documentAssistant.value.extractCertificateFields(finalText, documentTitle)
                        }.getOrElse {
                            CertificateFieldExtractor.extract(finalText, documentTitle)
                        }
                    }
                    status = when {
                        finalText.isBlank() -> "No readable text found"
                        certificateFields?.foundCount == 0 -> "Text extracted; no certificate fields found"
                        else -> "Text and certificate details extracted"
                    }
                    scanStep = ScanStep.RESULT
                    isProcessing = false
                }
            }
            .addOnFailureListener { error ->
                status = "OCR failed: ${error.localizedMessage ?: "unknown error"}"
                scanStep = ScanStep.RESULT
                isProcessing = false
            }
    }

    fun saveCurrentDocument() {
        if (extractedText.isBlank()) return
        scope.launch {
            isSaving = true
            status = "Saving document"
            try {
                val saved = withContext(Dispatchers.IO) {
                    repository.saveDocument(
                        id = activeDocumentId,
                        title = documentTitle.ifBlank { inferDocumentTitle(extractedText) },
                        text = extractedText,
                        preview = previewBitmap,
                        reviewNotes = reviewNotes,
                        highlightedPhrases = highlightedPhrases,
                        bookmarked = bookmarked,
                        highlightColor = highlightColor,
                        folderId = activeFolderId,
                        sourceType = sourceType,
                        recognitionMode = recognitionMode.name.lowercase()
                    )
                }
                activeDocumentId = saved.id
                documentTitle = saved.title
                refreshDocuments()
                status = "Saved to Documents"
            } catch (error: Exception) {
                status = "Could not save: ${error.localizedMessage ?: "unknown error"}"
            } finally {
                isSaving = false
            }
        }
    }

    fun askDocuments(question: String, folderId: String?) {
        if (question.isBlank() || assistantBusy) return
        scope.launch {
            assistantBusy = true
            assistantAnswer = try {
                withContext(Dispatchers.Default) {
                    documentAssistant.value.answer(question.trim(), documents, folderId)
                }
            } catch (error: Exception) {
                DocumentAnswer("The offline assistant could not start: ${error.localizedMessage ?: "unknown error"}")
            } finally {
                assistantBusy = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) loadImage(uri) else status = "Photo capture cancelled"
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri) else status = "No image selected"
    }
    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) loadImage(uri)
        else status = "Crop cancelled"
    }

    BackHandler(enabled = destination == AppDestination.SCAN && scanStep != ScanStep.CAPTURE) {
        scanStep = when {
            scanStep == ScanStep.RESULT && ocrBitmap != null -> ScanStep.PREVIEW
            else -> ScanStep.CAPTURE
        }
    }

    val appContent: @Composable (showMenu: Boolean) -> Unit = { showMenu ->
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (destination != AppDestination.SCAN) destination.label
                                else when (scanStep) {
                                    ScanStep.CAPTURE -> "Scan"
                                    ScanStep.PREVIEW -> "Preview"
                                    ScanStep.RESULT -> "Document"
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when (destination) {
                                    AppDestination.SCAN -> when (scanStep) {
                                        ScanStep.CAPTURE -> "Capture a page"
                                        ScanStep.PREVIEW -> "Check before extraction"
                                        ScanStep.RESULT -> "Review and save"
                                    }
                                    AppDestination.DOCUMENTS -> "Stored on this phone"
                                    AppDestination.ASSISTANT -> "Answers from your saved documents"
                                    AppDestination.ABOUT -> "Private by design"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        if (showMenu) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Open navigation")
                            }
                        }
                    },
                    actions = {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = "Offline mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 18.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                when (destination) {
                    AppDestination.SCAN -> when (scanStep) {
                        ScanStep.CAPTURE -> CaptureScreen(
                            mode = recognitionMode,
                            status = status,
                            onModeChange = { recognitionMode = it },
                            onTakePhoto = {
                                sourceType = "camera"
                                val photoFile = File.createTempFile("n152_scan_", ".jpg", context.cacheDir)
                                pendingPhotoUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    photoFile
                                )
                                cameraLauncher.launch(pendingPhotoUri)
                            },
                            onImportImage = {
                                sourceType = "gallery"
                                galleryLauncher.launch("image/*")
                            }
                        )

                        ScanStep.PREVIEW -> DocumentPreviewScreen(
                            bitmap = previewBitmap,
                            quality = imageQuality,
                            documentDetected = documentDetected,
                            enhancementMode = enhancementMode,
                            isProcessing = isProcessing,
                            status = status,
                            onEnhancementChange = { selected ->
                                if (selected != enhancementMode && !isProcessing) {
                                    enhancementMode = selected
                                    rebuildPreview(mode = selected)
                                }
                            },
                            onCrop = {
                                currentImageUri?.let { uri ->
                                    cropLauncher.launch(
                                        Intent(context, CropEditorActivity::class.java)
                                            .putExtra(CropEditorActivity.EXTRA_INPUT_URI, uri)
                                    )
                                }
                            },
                            onRotate = {
                                if (!isProcessing) {
                                    rotationDegrees = (rotationDegrees + 90) % 360
                                    rebuildPreview(rotation = rotationDegrees)
                                }
                            },
                            onRetake = ::newScan,
                            onExtract = ::extractText
                        )

                        ScanStep.RESULT -> ExtractedDocumentScreen(
                            text = extractedText,
                            bitmap = previewBitmap,
                            title = documentTitle,
                            certificateFields = certificateFields,
                            activeDocumentId = activeDocumentId,
                            stats = documentStats,
                            status = status,
                            isProcessing = isProcessing,
                            isSaving = isSaving,
                            reviewNotes = reviewNotes,
                            highlightedPhrases = highlightedPhrases,
                            bookmarked = bookmarked,
                            highlightColor = highlightColor,
                            onTitleChange = { documentTitle = it },
                            onTextChange = {
                                extractedText = it
                                documentStats = documentStats?.copy(
                                    text = it,
                                    lines = it.lineSequence().count(),
                                    words = Regex("\\S+").findAll(it).count()
                                )
                            },
                            onReviewNotesChange = { reviewNotes = it },
                            onHighlightsChange = { highlightedPhrases = ArrayList(it) },
                            onBookmarkedChange = { bookmarked = it },
                            onHighlightColorChange = { highlightColor = it },
                            onBackToPreview = { if (ocrBitmap != null) scanStep = ScanStep.PREVIEW },
                            onSave = ::saveCurrentDocument,
                            onCopy = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard.setPrimaryClip(ClipData.newPlainText("N152 document", extractedText))
                                status = "Document copied"
                            },
                            onShare = { shareText(context, extractedText) },
                            onNewScan = ::newScan
                        )
                    }

                    AppDestination.DOCUMENTS -> DocumentsScreen(
                        documents = documents,
                        folders = folders,
                        isLoading = documentsLoading,
                        onNewScan = ::newScan,
                        onOpenDocument = ::openDocument,
                        onCreateFolder = { name ->
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { repository.createFolder(name) } }
                                    .onSuccess { refreshDocuments() }
                                    .onFailure { status = it.localizedMessage ?: "Could not create folder" }
                            }
                        },
                        onMoveDocument = { document, folderId ->
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.moveDocument(document, folderId) }
                                refreshDocuments()
                            }
                        },
                        onDeleteDocument = { document ->
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.deleteDocument(document) }
                                refreshDocuments()
                            }
                        }
                    )

                    AppDestination.ASSISTANT -> DocumentAssistantScreen(
                        documents = documents,
                        folders = folders,
                        isThinking = assistantBusy,
                        answer = assistantAnswer,
                        onAsk = ::askDocuments,
                        onOpenDocument = { id -> documents.firstOrNull { it.id == id }?.let(::openDocument) }
                    )

                    AppDestination.ABOUT -> AboutScreen()
                }
            }
        }
    }

    BoxWithConstraints {
        if (maxWidth >= 840.dp) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(280.dp)) {
                        DrawerContent(destination, documents.size, ::navigate)
                    }
                }
            ) { appContent(false) }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        DrawerContent(destination, documents.size, ::navigate)
                    }
                }
            ) { appContent(true) }
        }
    }
}

@Composable
private fun DrawerContent(
    selected: AppDestination,
    documentCount: Int,
    onNavigate: (AppDestination) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            "N152",
            modifier = Modifier.padding(start = 16.dp, top = 28.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Your private document workspace",
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NavigationDrawerItem(
            label = { Text("Scan") },
            selected = selected == AppDestination.SCAN,
            icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
            onClick = { onNavigate(AppDestination.SCAN) }
        )
        NavigationDrawerItem(
            label = { Text("Documents") },
            selected = selected == AppDestination.DOCUMENTS,
            icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
            badge = { if (documentCount > 0) Text(documentCount.toString()) },
            onClick = { onNavigate(AppDestination.DOCUMENTS) }
        )
        NavigationDrawerItem(
            label = { Text("Ask documents") },
            selected = selected == AppDestination.ASSISTANT,
            icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
            onClick = { onNavigate(AppDestination.ASSISTANT) }
        )
        NavigationDrawerItem(
            label = { Text("About") },
            selected = selected == AppDestination.ABOUT,
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            onClick = { onNavigate(AppDestination.ABOUT) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))
        Icon(
            Icons.Outlined.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp)
        )
        Text(
            "No internet permission",
            modifier = Modifier.padding(start = 16.dp, top = 10.dp),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Scans and documents stay inside this app.",
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
    }

private fun createPreviewBitmap(source: Bitmap): Bitmap {
    val maxDimension = 1200
    val largest = max(source.width, source.height)
    if (largest <= maxDimension) return source.copy(Bitmap.Config.ARGB_8888, false)
    val scale = maxDimension.toDouble() / largest.toDouble()
    return Bitmap.createScaledBitmap(
        source,
        (source.width * scale).toInt().coerceAtLeast(1),
        (source.height * scale).toInt().coerceAtLeast(1),
        true
    )
}

private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return source.copy(Bitmap.Config.ARGB_8888, false)
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun inferDocumentTitle(text: String): String {
    val firstUsefulLine = text.lineSequence()
        .map(String::trim)
        .firstOrNull { it.length >= 3 }
        ?.take(52)
    if (!firstUsefulLine.isNullOrBlank()) return firstUsefulLine
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
    return "Scan $date"
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Scanned document")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share document"))
}

@Preview(showBackground = true)
@Composable
fun OcrAppPreview() {
    N152Theme { OcrApp() }
}
