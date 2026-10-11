package com.MAD.n152

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val LabBlue = Color(0xFF1F4BD8)
private val LabRed = Color(0xFFD62828)

/**
 * Pipeline Lab: runs the real image-processing stages on the current scan (or the bundled synthetic
 * certificate) and shows each intermediate result with the numbers that drive it.
 */
@Composable
fun PipelineLabScreen(currentScan: Bitmap?) {
    val context = LocalContext.current
    val hasScan = currentScan != null && !currentScan.isRecycled
    var useSample by rememberSaveable { mutableStateOf(!hasScan) }
    var stageIndex by rememberSaveable { mutableIntStateOf(0) }
    var gamma by rememberSaveable { mutableFloatStateOf(0.6f) }
    var appliedGamma by rememberSaveable { mutableFloatStateOf(0.6f) }
    var quality by rememberSaveable { mutableFloatStateOf(50f) }
    var appliedQuality by rememberSaveable { mutableFloatStateOf(50f) }
    var input by remember { mutableStateOf<LabInput?>(null) }
    var result by remember { mutableStateOf<LabStageResult?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val stage = LabStage.entries[stageIndex]
    val listState = rememberLazyListState()
    LaunchedEffect(stageIndex) { listState.scrollToItem(0) }

    // Copy the scan up front: the scan screen owns and may recycle its bitmap.
    val scanCopy = remember(currentScan) {
        currentScan?.takeUnless(Bitmap::isRecycled)?.copy(Bitmap.Config.ARGB_8888, false)
    }

    LaunchedEffect(useSample, scanCopy) {
        busy = true
        error = null
        input = null
        result = null
        input = runCatching {
            withContext(Dispatchers.Default) {
                if (!useSample && scanCopy != null) {
                    PipelineLabProcessor.prepare(scanCopy, "Current scan", null)
                } else {
                    val (bitmap, bytes) = PipelineLabProcessor.loadSample(context)
                    PipelineLabProcessor.prepare(bitmap, "Synthetic sample certificate (photo)", bytes)
                }
            }
        }.onFailure { error = it.localizedMessage ?: "Could not prepare the image" }.getOrNull()
        if (input == null) busy = false
    }

    val parameters = LabParameters(gamma = appliedGamma.toDouble(), quality = appliedQuality.roundToInt())
    LaunchedEffect(input, stage, parameters) {
        val prepared = input ?: return@LaunchedEffect
        busy = true
        error = null
        result = runCatching {
            withContext(Dispatchers.Default) { PipelineLabProcessor.compute(stage, prepared, parameters) }
        }.onFailure { error = it.localizedMessage ?: "Stage failed" }.getOrNull()
        busy = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = stageIndex,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LabStage.entries.forEachIndexed { index, item ->
                Tab(
                    selected = index == stageIndex,
                    onClick = { stageIndex = index },
                    text = { Text("${index + 1}. ${item.title}", maxLines = 1) }
                )
            }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            item {
                StageHeader(stage, stageIndex)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = !useSample,
                        enabled = hasScan,
                        onClick = { useSample = false },
                        label = { Text("Current scan") }
                    )
                    FilterChip(
                        selected = useSample,
                        onClick = { useSample = true },
                        label = { Text("Sample certificate") }
                    )
                }
            }
            if (stage == LabStage.POINT) {
                item {
                    ParameterSlider(
                        label = "Gamma",
                        valueText = String.format(Locale.US, "%.2f", gamma),
                        value = gamma,
                        range = 0.2f..3.0f,
                        onChange = { gamma = it },
                        onDone = { appliedGamma = gamma }
                    )
                }
            }
            if (stage == LabStage.DCT) {
                item {
                    ParameterSlider(
                        label = "JPEG quality",
                        valueText = quality.roundToInt().toString(),
                        value = quality,
                        range = 5f..95f,
                        onChange = { quality = it },
                        onDone = { appliedQuality = quality }
                    )
                }
            }
            error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            val current = result
            if (current == null) {
                item {
                    Text(
                        if (busy) "Processing on this phone" else "No result",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(panelRows(current.panels)) { row -> PanelRow(row) }
                items(current.histograms) { histogram -> HistogramCard(histogram) }
                item { MetricsCard(current.metrics) }
                if (current.note.isNotBlank()) {
                    item {
                        Text(
                            current.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageHeader(stage: LabStage, index: Int) {
    Column {
        Text(
            "STAGE ${index + 1} OF ${LabStage.entries.size}  ·  ${stage.unit.uppercase(Locale.US)}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = LabBlue
        )
        Spacer(Modifier.height(4.dp))
        Text(stage.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            stage.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(valueText, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
            }
            Slider(
                value = value,
                onValueChange = onChange,
                onValueChangeFinished = onDone,
                valueRange = range,
                modifier = Modifier.semantics { contentDescription = "$label slider" }
            )
        }
    }
}

private fun panelRows(panels: List<LabPanel>): List<List<LabPanel>> {
    val rows = ArrayList<List<LabPanel>>()
    var pending: LabPanel? = null
    panels.forEach { panel ->
        if (panel.wide) {
            pending?.let { rows += listOf(it); pending = null }
            rows += listOf(panel)
        } else if (pending == null) {
            pending = panel
        } else {
            rows += listOf(pending!!, panel)
            pending = null
        }
    }
    pending?.let { rows += listOf(it) }
    return rows
}

@Composable
private fun PanelRow(row: List<LabPanel>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { panel ->
            PanelCard(panel, Modifier.weight(1f))
        }
        if (row.size == 1 && !row[0].wide) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PanelCard(panel: LabPanel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        val ratio = panel.bitmap.width.toFloat() / max(1, panel.bitmap.height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111318), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            Image(
                bitmap = panel.bitmap.asImageBitmap(),
                contentDescription = panel.label,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.4f, 6f))
            )
        }
        Text(
            panel.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun HistogramCard(histogram: LabHistogram) {
    val barColor = MaterialTheme.colorScheme.onSurface
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(histogram.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .semantics { contentDescription = "Histogram: ${histogram.label}" }
            ) {
                // Square-root scale keeps the paper peak from flattening the ink levels.
                val peak = histogram.counts.maxOrNull()?.coerceAtLeast(1) ?: 1
                val barWidth = size.width / 256f
                histogram.counts.forEachIndexed { level, count ->
                    val h = (kotlin.math.sqrt(count.toDouble() / peak) * size.height).toFloat()
                    drawRect(
                        color = barColor,
                        topLeft = Offset(level * barWidth, size.height - h),
                        size = Size(barWidth.coerceAtLeast(1f), h)
                    )
                }
                histogram.marker?.let { t ->
                    val x = (t + 0.5f) * barWidth
                    drawLine(LabRed, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("0", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                histogram.marker?.let {
                    Text("T = $it", style = MaterialTheme.typography.labelSmall, color = LabRed, modifier = Modifier.weight(1f))
                }
                Text("255", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MetricsCard(metrics: List<Pair<String, String>>) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Key numbers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            metrics.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(128.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
