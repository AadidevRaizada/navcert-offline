package com.MAD.n152

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@Composable
fun ScanScreen(
    extractedText: String,
    documentTitle: String,
    activeDocumentId: String?,
    status: String,
    isProcessing: Boolean,
    isSaving: Boolean,
    handwritingMode: Boolean,
    previewBitmap: Bitmap?,
    imageQuality: ImageQuality?,
    documentDetected: Boolean,
    documentStats: DocumentText?,
    onHandwritingModeChange: (Boolean) -> Unit,
    onTakePhoto: () -> Unit,
    onImportImage: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "New scan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Capture a page or choose an image. Alignment and OCR run automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !handwritingMode,
                onClick = { onHandwritingModeChange(false) },
                label = { Text("Printed") },
                enabled = !isProcessing
            )
            FilterChip(
                selected = handwritingMode,
                onClick = { onHandwritingModeChange(true) },
                label = { Text("Handwriting") },
                enabled = !isProcessing
            )
        }

        Button(
            onClick = onTakePhoto,
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Scan with camera", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onImportImage,
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Choose image")
        }

        if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            status,
            style = MaterialTheme.typography.labelLarge,
            color = if (isProcessing) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        ScanPreview(
            bitmap = previewBitmap,
            quality = imageQuality,
            documentDetected = documentDetected
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Document", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                documentStats?.let { stats ->
                    Text(
                        "${stats.words} words, ${stats.lines} lines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (extractedText.isNotBlank()) {
                TextButton(onClick = onClear) { Text("Start over") }
            }
        }

        OutlinedTextField(
            value = documentTitle,
            onValueChange = onTitleChange,
            label = { Text("Document title") },
            singleLine = true,
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        OutlinedTextField(
            value = extractedText,
            onValueChange = onTextChange,
            label = { Text("Extracted text") },
            placeholder = { Text("Scanned text will appear here") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 11,
            enabled = !isProcessing,
            textStyle = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = Color(0xFF101828)
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFFCFCFD),
                unfocusedContainerColor = Color(0xFFFCFCFD),
                disabledContainerColor = Color(0xFFF2F4F7),
                focusedTextColor = Color(0xFF101828),
                unfocusedTextColor = Color(0xFF101828),
                disabledTextColor = Color(0xFF667085),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = Color(0xFF475467),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD0D5DD),
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(10.dp)
        )

        if (extractedText.isNotBlank()) {
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        isSaving -> "Saving"
                        activeDocumentId != null -> "Update document"
                        else -> "Save document"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }
        }

        Text(
            "For best results, fill the frame and keep the phone parallel to the page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp)
        )
    }
}

@Composable
private fun ScanPreview(
    bitmap: Bitmap?,
    quality: ImageQuality?,
    documentDetected: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            if (bitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Page preview", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Your enhanced scan appears here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Enhanced scanned document",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp)
                        .background(Color(0xFFEAECF0)),
                    contentScale = ContentScale.Fit
                )
            }

            if (quality != null) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = if (quality.isGood) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(quality.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            quality.guidance,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (documentDetected) "Aligned" else "Enhanced",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentsScreen(
    documents: List<StoredDocument>,
    folders: List<StoredFolder>,
    isLoading: Boolean,
    onNewScan: () -> Unit,
    onOpenDocument: (StoredDocument) -> Unit,
    onCreateFolder: (String) -> Unit,
    onMoveDocument: (StoredDocument, String?) -> Unit,
    onDeleteDocument: (StoredDocument) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var folderName by remember { mutableStateOf("") }
    var showCreateFolder by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<StoredDocument?>(null) }
    var pendingDelete by remember { mutableStateOf<StoredDocument?>(null) }
    val visibleDocuments = remember(documents, searchQuery, selectedFolderId) {
        documents.filter { selectedFolderId == null || it.folderId == selectedFolderId }.let { inFolder ->
            if (searchQuery.isBlank()) inFolder else inFolder.filter { document ->
            document.title.contains(searchQuery, ignoreCase = true) ||
                document.text.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            icon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
            title = { Text("Create folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it.take(60) },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = folderName.isNotBlank() && folders.none { it.name.equals(folderName.trim(), true) },
                    onClick = {
                        onCreateFolder(folderName)
                        folderName = ""
                        showCreateFolder = false
                    }
                ) { Text("Create folder") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolder = false }) { Text("Cancel") } }
        )
    }

    pendingMove?.let { document ->
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text("Move document") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listOf<StoredFolder?>(null).plus(folders).forEach { folder ->
                        val selected = document.folderId == folder?.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onMoveDocument(document, folder?.id)
                                pendingMove = null
                            },
                            label = { Text(folder?.name ?: "No folder") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingMove = null }) { Text("Cancel") } }
        )
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete document?") },
            text = { Text("This removes the stored text and preview from this phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete?.let(onDeleteDocument)
                        pendingDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = 28.dp
        )
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your documents", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Saved locally and available offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNewScan) {
                        Icon(Icons.Outlined.Add, contentDescription = "Start a new scan")
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search documents") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedFolderId == null,
                        onClick = { selectedFolderId = null },
                        label = { Text("All") }
                    )
                    folders.forEach { folder ->
                        FilterChip(
                            selected = selectedFolderId == folder.id,
                            onClick = { selectedFolderId = folder.id },
                            label = { Text(folder.name) }
                        )
                    }
                    TextButton(onClick = { showCreateFolder = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New folder")
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (isLoading) {
            items(3) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(92.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp)
                ) {}
            }
        } else if (visibleDocuments.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (searchQuery.isBlank()) "No saved documents yet" else "No matching documents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (searchQuery.isBlank()) "Scan a page and tap Save document."
                        else "Try another title or phrase.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (searchQuery.isBlank()) {
                        Button(onClick = onNewScan, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan first document")
                        }
                    }
                }
            }
        } else {
            gridItems(visibleDocuments, key = StoredDocument::id) { document ->
                Surface(
                    modifier = Modifier.padding(6.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    DocumentRow(
                        document = document,
                        onOpen = { onOpenDocument(document) },
                        onMove = { pendingMove = document },
                        onDelete = { pendingDelete = document }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(
    document: StoredDocument,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(width = 62.dp, height = 78.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (document.previewPath != null) {
                AsyncImage(
                    model = document.previewPath,
                    contentDescription = "Preview of ${document.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                document.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatStoredDate(document.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${document.wordCount} words, ${document.lineCount} lines · ${document.sourceType.replaceFirstChar(Char::uppercase)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                document.text.replace('\n', ' ').trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMove) {
            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Move ${document.title} to a folder")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${document.title}")
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
}

@Composable
fun DocumentAssistantScreen(
    documents: List<StoredDocument>,
    folders: List<StoredFolder>,
    isThinking: Boolean,
    answer: DocumentAnswer?,
    onAsk: (String, String?) -> Unit,
    onOpenDocument: (String) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ask your documents", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Answers are found on this phone and linked to the original document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFolderId == null,
                onClick = { selectedFolderId = null },
                label = { Text("All documents") }
            )
            folders.forEach { folder ->
                FilterChip(
                    selected = selectedFolderId == folder.id,
                    onClick = { selectedFolderId = folder.id },
                    label = { Text(folder.name) }
                )
            }
        }
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Ask a question") },
            supportingText = { Text("The answer must exist in your saved documents") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Button(
            onClick = { onAsk(question, selectedFolderId) },
            enabled = question.isNotBlank() && documents.isNotEmpty() && !isThinking,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isThinking) "Finding answer" else "Ask documents")
        }
        if (isThinking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (documents.isEmpty()) {
            Text("Save at least one document before asking a question.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        answer?.let { result ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Answer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(result.text, style = MaterialTheme.typography.bodyLarge)
                    if (result.documentId != null && result.documentTitle != null) {
                        HorizontalDivider()
                        TextButton(onClick = { onOpenDocument(result.documentId) }) {
                            Icon(Icons.Outlined.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open ${result.documentTitle}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        result.excerpt?.let { excerpt ->
                            Text(
                                excerpt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text("Private by design", style = MaterialTheme.typography.headlineSmall)
        Text(
            "N152 turns photos into editable documents without an account, server, or network permission.",
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AboutRow(
            icon = { Icon(Icons.Outlined.CloudOff, contentDescription = null) },
            title = "Works offline",
            description = "The OCR model is bundled inside the app."
        )
        HorizontalDivider()
        AboutRow(
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            title = "No network access",
            description = "Android internet permissions are removed from the final app."
        )
        HorizontalDivider()
        AboutRow(
            icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
            title = "Private storage",
            description = "Saved documents remain in the app's private device folder."
        )
        HorizontalDivider()
        AboutRow(
            icon = { Icon(Icons.Outlined.AutoFixHigh, contentDescription = null) },
            title = "Automatic cleanup",
            description = "OpenCV aligns pages and improves contrast before recognition."
        )
    }
}

@Composable
private fun AboutRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(description) },
        leadingContent = icon,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

private fun formatStoredDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
