package com.MAD.n152

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.MAD.n152.ui.theme.ReaderAccent
import com.MAD.n152.ui.theme.ReaderInk
import com.MAD.n152.ui.theme.ReaderPaper
import com.MAD.n152.ui.theme.ReaderToolbar

enum class ScanStep { CAPTURE, PREVIEW, RESULT }

enum class RecognitionMode(val label: String) {
    AUTO("Auto"),
    PRINTED("Printed"),
    HANDWRITING("Handwritten")
}

@Composable
fun CaptureScreen(
    mode: RecognitionMode,
    status: String,
    onModeChange: (RecognitionMode) -> Unit,
    onTakePhoto: () -> Unit,
    onImportImage: () -> Unit,
    onOpenLab: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.35f))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(20.dp).size(44.dp)
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "Scan a document",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Take a clear photo. You can crop and improve it before text extraction.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onTakePhoto,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text("Open camera", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onImportImage,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text("Choose an image")
        }
        TextButton(onClick = onOpenLab, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Outlined.Science, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("See the image pipeline, stage by stage")
        }
        Spacer(Modifier.weight(0.65f))
        Text(
            "Text type",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip(
                label = "Auto",
                selected = mode == RecognitionMode.AUTO,
                onClick = { onModeChange(RecognitionMode.AUTO) },
                modifier = Modifier.weight(1f)
            )
            ModeChip(
                label = "Printed",
                selected = mode == RecognitionMode.PRINTED,
                onClick = { onModeChange(RecognitionMode.PRINTED) },
                modifier = Modifier.weight(1f)
            )
            ModeChip(
                label = "Handwriting",
                selected = mode == RecognitionMode.HANDWRITING,
                onClick = { onModeChange(RecognitionMode.HANDWRITING) },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            if (mode == RecognitionMode.AUTO) "Automatic works for most pages."
            else if (mode == RecognitionMode.PRINTED) "Best for books, forms, and typed pages."
            else "Keeps uncertain handwritten words unchanged.",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (status != "Ready to scan") {
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier
    )
}

@Composable
fun DocumentPreviewScreen(
    bitmap: Bitmap?,
    quality: ImageQuality?,
    documentDetected: Boolean,
    enhancementMode: EnhancementMode,
    isProcessing: Boolean,
    status: String,
    onEnhancementChange: (EnhancementMode) -> Unit,
    onCrop: () -> Unit,
    onRotate: () -> Unit,
    onRetake: () -> Unit,
    onExtract: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                PreviewImage(bitmap, isProcessing, Modifier.weight(7f))
                PreviewTools(
                    quality, documentDetected, enhancementMode, isProcessing, bitmap != null, status,
                    onEnhancementChange, onCrop, onRotate, onRetake, onExtract,
                    Modifier.weight(3f).verticalScroll(rememberScrollState())
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                PreviewImage(bitmap, isProcessing, Modifier.weight(1f))
                PreviewTools(
                    quality, documentDetected, enhancementMode, isProcessing, bitmap != null, status,
                    onEnhancementChange, onCrop, onRotate, onRetake, onExtract
                )
            }
        }
    }
}

@Composable
private fun PreviewImage(bitmap: Bitmap?, isProcessing: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF111318))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Document preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Preparing preview", color = Color.White)
            }
        }
    }
}

@Composable
private fun PreviewTools(
    quality: ImageQuality?,
    documentDetected: Boolean,
    enhancementMode: EnhancementMode,
    isProcessing: Boolean,
    canExtract: Boolean,
    status: String,
    onEnhancementChange: (EnhancementMode) -> Unit,
    onCrop: () -> Unit,
    onRotate: () -> Unit,
    onRetake: () -> Unit,
    onExtract: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            Text("Scan tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoFixHigh,
                    contentDescription = null,
                    tint = if (quality?.isGood == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        quality?.label ?: status,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            quality?.isGood == false -> quality.guidance
                            documentDetected -> "Page borders found and aligned"
                            else -> "Check the page edges before extracting"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCrop, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Crop, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Crop")
                }
                OutlinedButton(onClick = onRotate, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Rotate")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhancementMode.entries.forEach { mode ->
                    ModeChip(
                        label = mode.label,
                        selected = enhancementMode == mode,
                        onClick = { onEnhancementChange(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = onExtract,
                enabled = canExtract && !isProcessing,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text(if (isProcessing) "Preparing" else "Extract text", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onRetake, enabled = !isProcessing, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Use another image")
            }
    }
}

@Composable
fun ExtractedDocumentScreen(
    text: String,
    bitmap: Bitmap?,
    title: String,
    certificateFields: CertificateFields?,
    activeDocumentId: String?,
    stats: DocumentText?,
    status: String,
    isProcessing: Boolean,
    isSaving: Boolean,
    reviewNotes: String,
    highlightedPhrases: List<String>,
    bookmarked: Boolean,
    highlightColor: Int,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onReviewNotesChange: (String) -> Unit,
    onHighlightsChange: (List<String>) -> Unit,
    onBookmarkedChange: (Boolean) -> Unit,
    onHighlightColorChange: (Int) -> Unit,
    onBackToPreview: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onNewScan: () -> Unit,
    onOpenLab: () -> Unit = {}
) {
    var toolsExpanded by rememberSaveable { mutableStateOf(true) }
    var showScanImage by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DocumentEditor(
                    text, bitmap, title, certificateFields, activeDocumentId, isProcessing, onTitleChange, onTextChange,
                    onBackToPreview, highlightedPhrases, onHighlightsChange, bookmarked,
                    onBookmarkedChange, highlightColor, onHighlightColorChange,
                    showScanImage, { showScanImage = it },
                    toolsExpanded, { toolsExpanded = !toolsExpanded },
                    Modifier.weight(7f).verticalScroll(rememberScrollState())
                )
                if (toolsExpanded) {
                    DocumentTools(
                        text, activeDocumentId, stats, status, isSaving, reviewNotes,
                        onReviewNotesChange, onSave, onCopy, onShare, onNewScan, onOpenLab,
                        { toolsExpanded = false },
                        Modifier.weight(3f).widthIn(min = 280.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DocumentEditor(
                    text, bitmap, title, certificateFields, activeDocumentId, isProcessing, onTitleChange, onTextChange,
                    onBackToPreview, highlightedPhrases, onHighlightsChange, bookmarked,
                    onBookmarkedChange, highlightColor, onHighlightColorChange,
                    showScanImage, { showScanImage = it },
                    toolsExpanded, { toolsExpanded = !toolsExpanded }
                )
                if (toolsExpanded) {
                    DocumentTools(
                        text, activeDocumentId, stats, status, isSaving, reviewNotes,
                        onReviewNotesChange, onSave, onCopy, onShare, onNewScan, onOpenLab,
                        { toolsExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentEditor(
    text: String,
    bitmap: Bitmap?,
    title: String,
    certificateFields: CertificateFields?,
    activeDocumentId: String?,
    isProcessing: Boolean,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onBackToPreview: () -> Unit,
    highlightedPhrases: List<String>,
    onHighlightsChange: (List<String>) -> Unit,
    bookmarked: Boolean,
    onBookmarkedChange: (Boolean) -> Unit,
    highlightColor: Int,
    onHighlightColorChange: (Int) -> Unit,
    showScanImage: Boolean,
    onShowScanImageChange: (Boolean) -> Unit,
    toolsExpanded: Boolean,
    onToggleTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editorValue by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    LaunchedEffect(text) {
        if (editorValue.text != text) editorValue = TextFieldValue(text, TextRange(text.length))
    }
    Column(
        modifier = modifier.background(ReaderPaper, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Document", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ReaderInk)
            }
            TextButton(onClick = onToggleTools) { Text(if (toolsExpanded) "Hide review" else "Show review") }
        }

        if (text.isBlank() && !isProcessing) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("No readable text found", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Try a tighter crop, better lighting, or the B&W enhancement.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onBackToPreview) { Text("Improve image") }
                }
            }
        } else if (activeDocumentId == null) {
            TextButton(onClick = onBackToPreview) { Text("Back to preview") }
        }

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Document title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ReaderPaper,
                unfocusedContainerColor = ReaderPaper,
                focusedTextColor = ReaderInk,
                unfocusedTextColor = ReaderInk,
                focusedLabelColor = ReaderInk,
                unfocusedLabelColor = ReaderInk.copy(alpha = 0.72f),
                focusedBorderColor = ReaderAccent,
                unfocusedBorderColor = ReaderInk.copy(alpha = 0.28f),
                cursorColor = ReaderInk
            )
        )
        if (certificateFields != null && certificateFields.foundCount > 0) {
            CertificateSummary(certificateFields)
        }
        if (bitmap != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeChip("Text", !showScanImage, { onShowScanImageChange(false) }, Modifier.weight(1f))
                ModeChip("Scan image", showScanImage, { onShowScanImageChange(true) }, Modifier.weight(1f))
            }
        }
        if (showScanImage && bitmap != null) {
            Surface(
                color = Color(0xFF111318),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(520.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Scanned document image",
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            FormattingToolbar(
                value = editorValue,
                highlightedPhrases = highlightedPhrases,
                bookmarked = bookmarked,
                highlightColor = highlightColor,
                onValueChange = { updated ->
                    editorValue = updated
                    onTextChange(updated.text)
                },
                onHighlightsChange = onHighlightsChange,
                onBookmarkedChange = onBookmarkedChange,
                onHighlightColorChange = onHighlightColorChange
            )
            OutlinedTextField(
                value = editorValue,
                onValueChange = { updated ->
                    editorValue = updated
                    onTextChange(updated.text)
                },
                label = { Text("Extracted text") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 15,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    lineHeight = 29.sp,
                    color = ReaderInk
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFFCFCFD),
                    unfocusedContainerColor = Color(0xFFFCFCFD),
                    focusedTextColor = Color(0xFF101828),
                    unfocusedTextColor = Color(0xFF101828),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = Color(0xFF475467),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFFD0D5DD),
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                visualTransformation = highlightTransformation(
                    highlightedPhrases,
                    Color(highlightColor)
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun CertificateSummary(fields: CertificateFields) {
    val values = listOf(
        "Certificate" to fields.certificateName,
        "Issued" to fields.issueDate,
        "Port / place" to fields.issuePort,
        "Authority" to fields.issuingAuthority,
        "Expires" to fields.expiryDate
    ).filter { !it.second.isNullOrBlank() }
    Surface(
        color = ReaderAccent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Certificate details", fontWeight = FontWeight.Bold, color = ReaderInk)
            values.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, modifier = Modifier.widthIn(min = 92.dp), color = ReaderInk.copy(alpha = 0.65f))
                    Text(value.orEmpty(), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, color = ReaderInk)
                }
            }
        }
    }
}

@Composable
private fun FormattingToolbar(
    value: TextFieldValue,
    highlightedPhrases: List<String>,
    bookmarked: Boolean,
    highlightColor: Int,
    onValueChange: (TextFieldValue) -> Unit,
    onHighlightsChange: (List<String>) -> Unit,
    onBookmarkedChange: (Boolean) -> Unit,
    onHighlightColorChange: (Int) -> Unit
) {
    val selected = value.text.substring(value.selection.min, value.selection.max).trim()
    Surface(
        color = ReaderToolbar,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ReaderAccent),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0xFFFFD978, 0xFFFFA6A6, 0xFF9FE7CC, 0xFFA9DDF2, 0xFFC7B5F5).forEach { swatch ->
                    val color = swatch.toInt()
                    Surface(
                        onClick = { onHighlightColorChange(color) },
                        color = Color(color),
                        shape = CircleShape,
                        border = if (highlightColor == color) BorderStroke(2.dp, ReaderInk) else null,
                        modifier = Modifier.size(32.dp)
                    ) {}
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (selected.isNotBlank()) onHighlightsChange((highlightedPhrases + selected).distinct())
                    },
                    enabled = selected.isNotBlank()
                ) {
                    Icon(Icons.Outlined.Highlight, contentDescription = "Highlight selected text", tint = ReaderInk)
                }
                IconButton(onClick = { onValueChange(formatSelectedLines(value, numbered = false)) }) {
                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Format as bullet list", tint = ReaderInk)
                }
                IconButton(onClick = { onValueChange(formatSelectedLines(value, numbered = true)) }) {
                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Format as numbered list", tint = ReaderInk)
                }
                IconButton(
                    onClick = { onHighlightsChange(emptyList()) },
                    enabled = highlightedPhrases.isNotEmpty()
                ) {
                    Icon(Icons.Outlined.FormatClear, contentDescription = "Clear highlights", tint = ReaderInk)
                }
                IconButton(onClick = { onBookmarkedChange(!bookmarked) }) {
                    Icon(
                        if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark document",
                        tint = if (bookmarked) Color(highlightColor) else ReaderInk
                    )
                }
            }
        }
    }
}

internal fun formatSelectedLines(value: TextFieldValue, numbered: Boolean): TextFieldValue {
    val start = value.text.substring(0, value.selection.min).lastIndexOf('\n') + 1
    val nextBreak = value.text.indexOf('\n', value.selection.max)
    val end = if (nextBreak < 0) value.text.length else nextBreak
    val replacement = value.text.substring(start, end).split('\n').mapIndexed { index, line ->
        val content = line.removePrefix("• ").replace(Regex("^\\d+\\.\\s+"), "")
        if (numbered) "${index + 1}. $content" else "• $content"
    }.joinToString("\n")
    val updated = value.text.replaceRange(start, end, replacement)
    return TextFieldValue(updated, TextRange(start, start + replacement.length))
}

private fun highlightTransformation(phrases: List<String>, color: Color) = VisualTransformation { input ->
    val builder = AnnotatedString.Builder(input)
    phrases.filter(String::isNotBlank).forEach { phrase ->
        var start = input.text.indexOf(phrase, ignoreCase = true)
        while (start >= 0) {
            builder.addStyle(SpanStyle(background = color), start, start + phrase.length)
            start = input.text.indexOf(phrase, start + phrase.length, ignoreCase = true)
        }
    }
    TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
}

@Composable
private fun DocumentTools(
    text: String,
    activeDocumentId: String?,
    stats: DocumentText?,
    status: String,
    isSaving: Boolean,
    reviewNotes: String,
    onReviewNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onNewScan: () -> Unit,
    onOpenLab: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stats?.let { "${it.words} words, ${it.lines} lines" } ?: status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close review pane")
            }
            TextButton(onClick = onNewScan) { Text("New scan") }
        }
        OutlinedTextField(
            value = reviewNotes,
            onValueChange = onReviewNotesChange,
            label = { Text("Comments and notes") },
            supportingText = { Text("Saved only on this device") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )
        Button(
            onClick = onSave,
            enabled = text.isNotBlank() && !isSaving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.Save, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(
                if (isSaving) "Saving" else if (activeDocumentId == null) "Save document" else "Update document",
                fontWeight = FontWeight.Bold
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCopy, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(7.dp))
                Text("Copy text")
            }
            OutlinedButton(onClick = onShare, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.size(7.dp))
                Text("Share")
            }
        }
        OutlinedButton(onClick = onOpenLab, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Science, contentDescription = null)
            Spacer(Modifier.size(7.dp))
            Text("Open Pipeline Lab for this page")
        }
        Text(
            status,
            modifier = Modifier.padding(bottom = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
