package com.nileshlohar.imagequalityanalyzer.demo

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nileshlohar.imagequalityanalyzer.demo.ui.theme.ImageQualityAnalyzerDemoTheme
import com.nileshlohar.imagequalityanalyzer.ImageQualityAnalyzer
import com.nileshlohar.imagequalityanalyzer.ImageQualityResult
import com.nileshlohar.imagequalityanalyzer.ImageQualitySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImageQualityDemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var report by remember(context) {
        mutableStateOf(AnnotatedString(context.getString(R.string.initial_report)))
    }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    DisposableEffect(previewBitmap) {
        val held = previewBitmap
        onDispose {
            held?.recycle()
        }
    }

    suspend fun finishWithResult(
        label: String,
        analysis: ImageQualityResult,
        summary: ImageQualitySummary,
        isBlurry: Boolean,
        isBlurryVarianceOfLaplacian: Boolean,
        isDark: Boolean,
        fullBitmap: Bitmap,
    ) {
        val preview = withContext(Dispatchers.Default) {
            bitmapForPreview(fullBitmap, maxEdge = 720)
        }
        if (preview !== fullBitmap) {
            fullBitmap.recycle()
        }
        withContext(Dispatchers.Main) {
            previewBitmap = preview
            report = formatReport(
                context,
                label,
                analysis,
                summary,
                isBlurry,
                isBlurryVarianceOfLaplacian,
                isDark,
            )
            isBusy = false
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(
        contract = PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) {
            report = AnnotatedString(context.getString(R.string.no_image_selected))
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isBusy = true
            previewBitmap = null
            report = AnnotatedString(context.getString(R.string.analyzing))
            val full = withContext(Dispatchers.IO) {
                loadBitmapFromUri(context, uri)
            }
            if (full == null) {
                report = AnnotatedString(context.getString(R.string.could_not_load_image))
                isBusy = false
                return@launch
            }
            val analysis = withContext(Dispatchers.Default) {
                ImageQualityAnalyzer.analyze(full)
            }
            val summary = ImageQualityAnalyzer.summarize(analysis)
            val isBlurry = ImageQualityAnalyzer.isBlurry(analysis)
            val isBlurryVarianceOfLaplacian = ImageQualityAnalyzer.isBlurryVarianceOfLaplacian(analysis)
            val isDark = ImageQualityAnalyzer.isDark(analysis)
            finishWithResult(
                context.getString(R.string.selected_image_label),
                analysis,
                summary,
                isBlurry,
                isBlurryVarianceOfLaplacian,
                isDark,
                full,
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = {
                pickPhoto.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pick_photo))
        }
        if (isBusy) {
            Text(stringResource(R.string.analyzing), style = MaterialTheme.typography.bodySmall)
        }
        previewBitmap?.let { bitmap ->
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.selected_image_preview),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = report,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImageQualityDemoPreview() {
    ImageQualityAnalyzerDemoTheme {
        Text(stringResource(R.string.preview_run_app))
    }
}
