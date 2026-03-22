package com.nileshlohar.imagequalityanalyzer.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlin.math.max
import kotlin.math.min

private const val MAX_DECODE_EDGE = 2048

private fun sampleSizeForBounds(width: Int, height: Int, maxEdge: Int): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    var sampleSize = 1
    while (max(width / sampleSize, height / sampleSize) > maxEdge) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

internal fun loadBitmapFromUri(
    context: Context,
    uri: Uri,
    maxDecodeEdge: Int = MAX_DECODE_EDGE,
): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri),
            ) { decoder, info, _ ->
                val sourceSize = info.size
                val sampleSize = sampleSizeForBounds(sourceSize.width, sourceSize.height, maxDecodeEdge)
                if (sampleSize > 1) {
                    decoder.setTargetSampleSize(sampleSize)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeForBounds(bounds.outWidth, bounds.outHeight, maxDecodeEdge)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

/** Downscale for on-screen preview only; analysis runs on the decoded bitmap first. */
internal fun bitmapForPreview(source: Bitmap, maxEdge: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (w <= maxEdge && h <= maxEdge) return source
    val scale = min(maxEdge.toFloat() / w, maxEdge.toFloat() / h)
    val nw = max(1, (w * scale).toInt())
    val nh = max(1, (h * scale).toInt())
    return Bitmap.createScaledBitmap(source, nw, nh, true)
}
