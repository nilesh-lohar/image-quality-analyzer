package com.nileshlohar.imagequalityanalyzer

import android.graphics.Bitmap
import android.os.Build
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Grayscale (Rec. 601), a light Gaussian-style prefilter, Laplacian variance with
 * **reflect-101 borders** (close to OpenCV's default border mode for these operators), Sobel
 * **Tenengrad** mean energy, and **modified Laplacian** mean.
 */
internal object InternalMetrics {

    private data class LumaImage(
        val width: Int,
        val height: Int,
        val luma: DoubleArray,
    )

    private data class RawSharpnessMetrics(
        val laplacianVariance: Double,
        val tenengradMeanEnergy: Double,
        val modifiedLaplacianMean: Double,
    )

    fun analyze(bitmap: Bitmap, config: AnalysisConfig): ImageQualityResult {
        val lumaImage = bitmapToLuma(bitmap, config.maxSideLength)
        val filteredLuma = gaussianBlur3x3(lumaImage.luma, lumaImage.width, lumaImage.height)
        val sharpnessLuma = compensateLowLightContrast(filteredLuma)
        val blur = computeBlur(sharpnessLuma, lumaImage.width, lumaImage.height, config)
        val spatial = computeSpatialBlur(sharpnessLuma, lumaImage.width, lumaImage.height, config, blur.compositeSharpness)
        val darkness = computeDarkness(lumaImage.luma, lumaImage.width, lumaImage.height, config)
        return ImageQualityResult(
            blur = blur,
            spatial = spatial,
            darkness = darkness,
            analyzedWidth = lumaImage.width,
            analyzedHeight = lumaImage.height,
        )
    }

    /**
     * [Bitmap.getPixels] requires a CPU-backed bitmap. [ImageDecoder] often returns
     * [Bitmap.Config.HARDWARE], which must be copied to [Bitmap.Config.ARGB_8888] first.
     * The original [source] is never recycled here.
     */
    private fun bitmapToLuma(source: Bitmap, maxSide: Int): LumaImage {
        val decoded =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)
                    ?: error("Could not copy HARDWARE bitmap to ARGB_8888 for analysis")
            } else {
                source
            }

        var work = decoded
        var w = work.width
        var h = work.height
        val longest = max(w, h)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            val nw = max(1, (w * scale).toInt())
            val nh = max(1, (h * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(work, nw, nh, true)
            if (scaled !== work && work !== source) {
                work.recycle()
            }
            work = scaled
            w = work.width
            h = work.height
        }
        val pixels = IntArray(w * h)
        work.getPixels(pixels, 0, w, 0, 0, w, h)
        if (work !== source) {
            work.recycle()
        }
        val luma = DoubleArray(w * h)
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            luma[i] = 0.299 * r + 0.587 * g + 0.114 * b
            i++
        }
        return LumaImage(width = w, height = h, luma = luma)
    }

    private fun reflect101(index: Int, size: Int): Int {
        if (size <= 1) return 0
        var i = index
        while (i < 0 || i >= size) {
            i = if (i < 0) {
                -i
            } else {
                2 * size - i - 2
            }
        }
        return i
    }

    private fun lumaAt(luma: DoubleArray, w: Int, h: Int, x: Int, y: Int): Double {
        val cx = reflect101(x, w)
        val cy = reflect101(y, h)
        return luma[cy * w + cx]
    }

    private fun rectLumaAt(
        luma: DoubleArray,
        imageWidth: Int,
        x0: Int,
        y0: Int,
        rectWidth: Int,
        rectHeight: Int,
        x: Int,
        y: Int,
    ): Double {
        val gx = x0 + reflect101(x, rectWidth)
        val gy = y0 + reflect101(y, rectHeight)
        return luma[gy * imageWidth + gx]
    }

    private fun gaussianBlur3x3(source: DoubleArray, w: Int, h: Int): DoubleArray {
        if (w <= 1 || h <= 1) return source.copyOf()

        val horizontal = DoubleArray(source.size)
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val left = source[rowOffset + reflect101(x - 1, w)]
                val center = source[rowOffset + x]
                val right = source[rowOffset + reflect101(x + 1, w)]
                horizontal[rowOffset + x] = (left + 2.0 * center + right) * 0.25
            }
        }

        val blurred = DoubleArray(source.size)
        for (y in 0 until h) {
            val upY = reflect101(y - 1, h)
            val downY = reflect101(y + 1, h)
            for (x in 0 until w) {
                val centerIndex = y * w + x
                blurred[centerIndex] =
                    (
                        horizontal[upY * w + x] +
                            2.0 * horizontal[centerIndex] +
                            horizontal[downY * w + x]
                        ) * 0.25
            }
        }
        return blurred
    }

    /**
     * Blur operators respond to local contrast, not just focus. Very dark images can therefore look
     * artificially "soft" because their gradients are numerically small even when edges are still
     * present. Apply a mild contrast boost only for low-light, low-contrast inputs so sharpness
     * stays less coupled to exposure while darkness is still measured from the original luma.
     */
    private fun compensateLowLightContrast(source: DoubleArray): DoubleArray {
        if (source.isEmpty()) return source.copyOf()

        var sum = 0.0
        var sumSq = 0.0
        for (value in source) {
            sum += value
            sumSq += value * value
        }
        val count = source.size.toDouble()
        val mean = sum / count
        val variance = max(0.0, (sumSq / count) - mean * mean)
        val std = sqrt(variance)

        val lowLightMeanThreshold = 85.0
        val lowContrastStdThreshold = 36.0
        val targetStd = 44.0
        val maxGain = 1.45

        if (mean >= lowLightMeanThreshold || std >= lowContrastStdThreshold || std <= 1e-6) {
            return source.copyOf()
        }

        val gain = min(maxGain, targetStd / std)
        return DoubleArray(source.size) { index ->
            mean + (source[index] - mean) * gain
        }
    }

    private fun laplacianAt(luma: DoubleArray, w: Int, h: Int, x: Int, y: Int): Double {
        val c = lumaAt(luma, w, h, x, y)
        return (
            lumaAt(luma, w, h, x - 1, y) +
                lumaAt(luma, w, h, x + 1, y) +
                lumaAt(luma, w, h, x, y - 1) +
                lumaAt(luma, w, h, x, y + 1) -
                4.0 * c
            )
    }

    private fun sobelGxAt(luma: DoubleArray, w: Int, h: Int, x: Int, y: Int): Double {
        return (
            -1.0 * lumaAt(luma, w, h, x - 1, y - 1) +
                1.0 * lumaAt(luma, w, h, x + 1, y - 1) +
                -2.0 * lumaAt(luma, w, h, x - 1, y) +
                2.0 * lumaAt(luma, w, h, x + 1, y) +
                -1.0 * lumaAt(luma, w, h, x - 1, y + 1) +
                1.0 * lumaAt(luma, w, h, x + 1, y + 1)
            )
    }

    private fun sobelGyAt(luma: DoubleArray, w: Int, h: Int, x: Int, y: Int): Double {
        return (
            -1.0 * lumaAt(luma, w, h, x - 1, y - 1) +
                -2.0 * lumaAt(luma, w, h, x, y - 1) +
                -1.0 * lumaAt(luma, w, h, x + 1, y - 1) +
                1.0 * lumaAt(luma, w, h, x - 1, y + 1) +
                2.0 * lumaAt(luma, w, h, x, y + 1) +
                1.0 * lumaAt(luma, w, h, x + 1, y + 1)
            )
    }

    private fun modifiedLaplacianAt(luma: DoubleArray, w: Int, h: Int, x: Int, y: Int): Double {
        val cx = lumaAt(luma, w, h, x, y)
        val mx = kotlin.math.abs(2.0 * cx - lumaAt(luma, w, h, x - 1, y) - lumaAt(luma, w, h, x + 1, y))
        val my = kotlin.math.abs(2.0 * cx - lumaAt(luma, w, h, x, y - 1) - lumaAt(luma, w, h, x, y + 1))
        return mx + my
    }

    private fun saturate01(value: Double, scale: Double): Double {
        if (scale <= 0) return 0.0
        return 1.0 - exp(-value / scale)
    }

    private fun normalizeSharpness(metrics: RawSharpnessMetrics, config: AnalysisConfig): Double {
        val (sw1, sw2, sw3) = config.blurSaturationScales
        val (w1, w2, w3) = config.blurFusionWeights
        val s1 = saturate01(metrics.laplacianVariance, sw1)
        val s2 = saturate01(metrics.tenengradMeanEnergy, sw2)
        val s3 = saturate01(metrics.modifiedLaplacianMean, sw3)
        val tw = w1 + w2 + w3
        return ((w1 * s1 + w2 * s2 + w3 * s3) / tw).coerceIn(0.0, 1.0)
    }

    private fun computeRawMetrics(
        luma: DoubleArray,
        w: Int,
        h: Int,
    ): RawSharpnessMetrics {
        var sumLap = 0.0
        var sumLapSq = 0.0
        var sumTen = 0.0
        var sumMlap = 0.0
        var n = 0L
        for (y in 0 until h) {
            for (x in 0 until w) {
                val lap = laplacianAt(luma, w, h, x, y)
                sumLap += lap
                sumLapSq += lap * lap
                val gx = sobelGxAt(luma, w, h, x, y)
                val gy = sobelGyAt(luma, w, h, x, y)
                sumTen += gx * gx + gy * gy
                sumMlap += modifiedLaplacianAt(luma, w, h, x, y)
                n++
            }
        }
        val count = n.toDouble()
        val meanLap = sumLap / count
        val lapVar = (sumLapSq / count) - meanLap * meanLap
        val tenMean = sumTen / count
        val mlapMean = sumMlap / count

        return RawSharpnessMetrics(
            laplacianVariance = lapVar,
            tenengradMeanEnergy = tenMean,
            modifiedLaplacianMean = mlapMean,
        )
    }

    /** Same normalization as [computeBlur] but limited to a region and evaluated with local reflect-101 borders. */
    private fun computeCompositeSharpnessInRect(
        luma: DoubleArray,
        imageWidth: Int,
        x0: Int,
        x1Exclusive: Int,
        y0: Int,
        y1Exclusive: Int,
        config: AnalysisConfig,
    ): Double? {
        val rectWidth = x1Exclusive - x0
        val rectHeight = y1Exclusive - y0
        if (rectWidth <= 0 || rectHeight <= 0) return null

        var sumLap = 0.0
        var sumLapSq = 0.0
        var sumTen = 0.0
        var sumMlap = 0.0
        var n = 0L
        for (y in 0 until rectHeight) {
            for (x in 0 until rectWidth) {
                val c = rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x, y)
                val lap =
                    rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x - 1, y) +
                        rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x + 1, y) +
                        rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x, y - 1) +
                        rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x, y + 1) -
                        4.0 * c
                val gx =
                    -1.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x - 1, y - 1) +
                        1.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x + 1, y - 1) +
                        -2.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x - 1, y) +
                        2.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x + 1, y) +
                        -1.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x - 1, y + 1) +
                        1.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x + 1, y + 1)
                val gy =
                    -1.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x - 1, y - 1) +
                        -2.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x, y - 1) +
                        -1.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x + 1, y - 1) +
                        1.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x - 1, y + 1) +
                        2.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x, y + 1) +
                        1.0 * rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x + 1, y + 1)
                val mx = kotlin.math.abs(
                    2.0 * c -
                        rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x - 1, y) -
                        rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x + 1, y),
                )
                val my = kotlin.math.abs(
                    2.0 * c -
                        rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x, y - 1) -
                        rectLumaAt(luma, imageWidth, x0, y0, rectWidth, rectHeight, x, y + 1),
                )
                sumLap += lap
                sumLapSq += lap * lap
                sumTen += gx * gx + gy * gy
                sumMlap += mx + my
                n++
            }
        }
        if (n < 32) return null
        val count = n.toDouble()
        val meanLap = sumLap / count
        val lapVar = (sumLapSq / count) - meanLap * meanLap
        val metrics = RawSharpnessMetrics(
            laplacianVariance = lapVar,
            tenengradMeanEnergy = sumTen / count,
            modifiedLaplacianMean = sumMlap / count,
        )
        return normalizeSharpness(metrics, config)
    }

    private fun computeBlur(luma: DoubleArray, w: Int, h: Int, config: AnalysisConfig): BlurMetrics {
        require(w >= 3 && h >= 3) { "Image too small after scaling" }
        val metrics = computeRawMetrics(luma, w, h)
        val composite = normalizeSharpness(metrics, config)

        return BlurMetrics(
            laplacianVariance = metrics.laplacianVariance,
            tenengradMeanEnergy = metrics.tenengradMeanEnergy,
            modifiedLaplacianMean = metrics.modifiedLaplacianMean,
            compositeSharpness = composite,
        )
    }

    private fun computeSpatialBlur(
        luma: DoubleArray,
        w: Int,
        h: Int,
        config: AnalysisConfig,
        globalSharpness: Double,
    ): SpatialBlurMetrics {
        val rows = config.spatialGridRows
        val cols = config.spatialGridCols
        if (w < 8 || h < 8) {
            val base = classifySpatial(listOf(globalSharpness), 1, 1)
            return base.copy(
                gridRows = 1,
                gridCols = 1,
                regionSharpness = listOf(globalSharpness),
                sharpnessSpread = 0.0,
                explanation = "Image too small for a multi-region map. ${base.explanation}",
            )
        }

        if (rows == 1 && cols == 1) {
            return classifySpatial(listOf(globalSharpness), 1, 1)
        }

        val cellW = w / cols
        val cellH = h / rows
        val regions = ArrayList<Double>(rows * cols)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x0 = col * cellW
                val x1 = if (col == cols - 1) w else (col + 1) * cellW
                val y0 = row * cellH
                val y1 = if (row == rows - 1) h else (row + 1) * cellH
                val s = computeCompositeSharpnessInRect(luma, w, x0, x1, y0, y1, config)
                regions.add(s ?: globalSharpness)
            }
        }

        return classifySpatial(regions, rows, cols)
    }

    private fun classifySpatial(
        regions: List<Double>,
        rows: Int,
        cols: Int,
    ): SpatialBlurMetrics {
        val minR = regions.minOrNull()!!
        val maxR = regions.maxOrNull()!!
        val spread = (maxR - minR).coerceAtLeast(0.0)
        val meanR = regions.average()
        val high = 0.44
        val low = 0.20
        val spreadMixed = 0.18
        val tight = 0.08
        val highCount = regions.count { it >= high }
        val lowCount = regions.count { it <= low }
        val n = regions.size

        val strongContrast = maxR >= high && minR <= low

        val (category, explanation) = when {
            spread < tight && meanR >= high ->
                SpatialSharpnessCategory.FULL_FRAME_CLEAR to
                    "Sharpness is similar in all regions—the whole frame looks consistently clear."
            spread < tight && meanR <= low ->
                SpatialSharpnessCategory.FULL_FRAME_SOFT to
                    "All regions look similarly soft; blur is fairly even across the image."
            spread >= spreadMixed || strongContrast -> {
                val detail = when {
                    highCount >= n * 0.75 ->
                        "Most of the frame is sharp, but at least one region is noticeably softer."
                    lowCount >= n * 0.75 ->
                        "Most of the frame is soft, with only smaller sharper patches."
                    else ->
                        "Some regions are clearly sharper than others. That often happens when only part of the scene is in focus (shallow depth of field) or focus falls on one side. The single whole-image score can still look \"clear\" because sharp areas pull the average up."
                }
                SpatialSharpnessCategory.MIXED_CLEAR_AND_SOFT to
                    "Mixed sharpness: not the same everywhere. $detail"
            }
            meanR >= high ->
                SpatialSharpnessCategory.MOSTLY_CLEAR to
                    "Mostly clear overall, with mild unevenness between regions."
            meanR <= low ->
                SpatialSharpnessCategory.MOSTLY_SOFT to
                    "Mostly soft overall, with limited sharper areas."
            else ->
                SpatialSharpnessCategory.MODERATE_UNEVEN to
                    "Sharpness varies somewhat between regions—not extreme, but not perfectly uniform."
        }

        return SpatialBlurMetrics(
            gridRows = rows,
            gridCols = cols,
            regionSharpness = regions,
            sharpnessSpread = spread,
            category = category,
            explanation = explanation,
        )
    }

    private fun computeDarkness(luma: DoubleArray, w: Int, h: Int, config: AnalysisConfig): DarknessMetrics {
        var sum = 0.0
        var sumSq = 0.0
        var veryDark = 0L
        val thresh = config.veryDarkLumaThreshold
        val n = luma.size.toDouble()
        for (v in luma) {
            val d = v.toDouble()
            sum += d
            sumSq += d * d
            if (v < thresh) veryDark++
        }
        val mean = sum / n
        val variance = (sumSq / n) - mean * mean
        val std = sqrt(max(0.0, variance))
        val fracDark = veryDark / n

        val meanDarkness = 1.0 - (mean / 255.0)
        val cd =
            (config.darknessVeryDarkWeight * fracDark + config.darknessMeanWeight * meanDarkness) /
                (config.darknessVeryDarkWeight + config.darknessMeanWeight)

        return DarknessMetrics(
            meanLuminance = mean,
            stdLuminance = std,
            fractionVeryDark = fracDark,
            compositeDarkness = cd.coerceIn(0.0, 1.0),
        )
    }
}
