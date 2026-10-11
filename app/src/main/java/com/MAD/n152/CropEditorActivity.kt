package com.MAD.n152

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.MAD.n152.ui.theme.N152Theme
import com.MAD.n152.ui.theme.ReaderAccent
import com.MAD.n152.ui.theme.ReaderInk
import com.canhub.cropper.CropImageView
import java.io.File

class CropEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_INPUT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_INPUT_URI)
        }

        if (inputUri == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            N152Theme {
                CropEditorScreen(
                    inputUri = inputUri,
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onComplete = { uri ->
                        setResult(Activity.RESULT_OK, Intent().setData(uri))
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_INPUT_URI = "com.MAD.n152.extra.INPUT_URI"
    }
}

@Composable
private fun CropEditorScreen(
    inputUri: Uri,
    onCancel: () -> Unit,
    onComplete: (Uri) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var cropView by remember { mutableStateOf<CropImageView?>(null) }
    var isCropping by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(cropView) {
        onDispose {
            cropView?.setOnCropImageCompleteListener(null)
            cropView?.clearImage()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0E1014)) {
        // targetSdk 35+ draws edge to edge, so keep the toolbar and buttons clear of the system bars.
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = ReaderAccent)
                ) { Text("Cancel") }
                Text(
                    "Crop page",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Drag the corners",
                    color = Color(0xFFB9C2D0),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            AndroidView(
                factory = { viewContext ->
                    CropImageView(viewContext).apply {
                        guidelines = CropImageView.Guidelines.ON
                        cropShape = CropImageView.CropShape.RECTANGLE
                        setFixedAspectRatio(false)
                        setMultiTouchEnabled(true)
                        setImageUriAsync(inputUri)
                        cropView = this
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            )

            errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { cropView?.rotateImage(-90) }, enabled = !isCropping) {
                    Icon(Icons.AutoMirrored.Outlined.RotateLeft, contentDescription = "Rotate left", tint = Color.White)
                }
                IconButton(onClick = { cropView?.rotateImage(90) }, enabled = !isCropping) {
                    Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "Rotate right", tint = Color.White)
                }
                Button(
                    onClick = {
                        val view = cropView ?: return@Button
                        val outputFile = File.createTempFile("n152_crop_", ".jpg", context.cacheDir)
                        val outputUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            outputFile
                        )
                        isCropping = true
                        errorMessage = null
                        view.setOnCropImageCompleteListener { _, result ->
                            isCropping = false
                            if (result.isSuccessful && result.uriContent != null) {
                                onComplete(result.uriContent!!)
                            } else {
                                errorMessage = result.error?.localizedMessage ?: "Could not crop this image"
                            }
                        }
                        view.croppedImageAsync(
                            saveCompressFormat = Bitmap.CompressFormat.JPEG,
                            saveCompressQuality = 96,
                            options = CropImageView.RequestSizeOptions.NONE,
                            customOutputUri = outputUri
                        )
                    },
                    enabled = !isCropping,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReaderAccent,
                        contentColor = ReaderInk
                    )
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Text(if (isCropping) " Cropping" else " Apply crop")
                }
            }
        }
    }
}
