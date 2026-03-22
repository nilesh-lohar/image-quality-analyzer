package com.nileshlohar.imagequalityanalyzer

import android.graphics.Bitmap

/**
 * Image sharpness (blur) and darkness analysis without native OpenCV.
 *
 * Blur detection is anchored around an **OpenCV-like variance of Laplacian** path, with
 * **Tenengrad** (Sobel gradient energy) and **modified Laplacian** used as lighter supporting
 * signals. Internally it uses reflect-101 borders and a small Gaussian-style prefilter so the
 * result tracks common OpenCV pipelines more closely while staying lightweight and dependency-free.
 *
 * For **maximum parity with desktop OpenCV**, use the official OpenCV Android SDK and call the same
 * functions you use on PC; this class stays lightweight and JitPack-friendly.
 */
object ImageQualityAnalyzer {

    const val DEFAULT_BLURRY_THRESHOLD: Double = 0.22
    const val DEFAULT_VARIANCE_OF_LAPLACIAN_THRESHOLD: Double = 120.0
    const val DEFAULT_DARK_THRESHOLD: Double = 0.55
    const val DEFAULT_DARK_MEAN_LUMINANCE_THRESHOLD: Double = 95.0
    const val DEFAULT_PARTIAL_DARK_FRACTION_THRESHOLD: Double = 0.18
    const val DEFAULT_PARTIAL_DARK_MAX_MEAN_LUMINANCE: Double = 140.0

    @JvmStatic
    @JvmOverloads
    fun analyze(bitmap: Bitmap, config: AnalysisConfig = AnalysisConfig()): ImageQualityResult {
        return InternalMetrics.analyze(bitmap, config)
    }

    @JvmStatic
    fun summarize(result: ImageQualityResult): ImageQualitySummary {
        return ImageQualityText.summarize(result)
    }

    @JvmStatic
    fun formatReport(label: String, result: ImageQualityResult): String {
        return ImageQualityText.formatReport(label, result)
    }

    @JvmStatic
    @JvmOverloads
    fun isBlurry(
        result: ImageQualityResult,
        blurryThreshold: Double = DEFAULT_BLURRY_THRESHOLD,
    ): Boolean {
        val overallThreshold = blurryThreshold * 0.9
        val regionThreshold = blurryThreshold * 0.92
        val overallBlur =
            result.blur.compositeSharpness <= overallThreshold &&
                result.blur.laplacianVariance < DEFAULT_VARIANCE_OF_LAPLACIAN_THRESHOLD * 1.1
        val softRegionCount = result.spatial.regionSharpness.count { it <= regionThreshold }
        val totalRegionCount = result.spatial.regionSharpness.size.coerceAtLeast(1)
        val substantialSoftArea = softRegionCount * 5 >= totalRegionCount * 3
        val mixedHalfBlur =
            result.spatial.category == SpatialSharpnessCategory.MIXED_CLEAR_AND_SOFT &&
                softRegionCount * 2 >= totalRegionCount &&
                result.spatial.sharpnessSpread >= 0.28
        val spatiallyBlurred = when (result.spatial.category) {
            SpatialSharpnessCategory.FULL_FRAME_SOFT,
            SpatialSharpnessCategory.MOSTLY_SOFT,
            -> true
            else -> false
        }
        return overallBlur || spatiallyBlurred || substantialSoftArea || mixedHalfBlur
    }

    @JvmStatic
    @JvmOverloads
    fun isBlurry(
        bitmap: Bitmap,
        config: AnalysisConfig = AnalysisConfig(),
        blurryThreshold: Double = DEFAULT_BLURRY_THRESHOLD,
    ): Boolean {
        return isBlurry(analyze(bitmap, config), blurryThreshold)
    }

    /**
     * Global blur check based on variance of Laplacian only.
     *
     * This does not use regional heuristics. It follows the common
     * `variance_of_laplacian < threshold` rule that is frequently implemented with OpenCV, but the
     * threshold is application-specific rather than an official OpenCV constant.
     */
    @JvmStatic
    @JvmOverloads
    fun isBlurryVarianceOfLaplacian(
        result: ImageQualityResult,
        laplacianVarianceThreshold: Double = DEFAULT_VARIANCE_OF_LAPLACIAN_THRESHOLD,
    ): Boolean {
        return result.blur.laplacianVariance < laplacianVarianceThreshold
    }

    @JvmStatic
    @JvmOverloads
    fun isBlurryVarianceOfLaplacian(
        bitmap: Bitmap,
        config: AnalysisConfig = AnalysisConfig(),
        laplacianVarianceThreshold: Double = DEFAULT_VARIANCE_OF_LAPLACIAN_THRESHOLD,
    ): Boolean {
        return isBlurryVarianceOfLaplacian(analyze(bitmap, config), laplacianVarianceThreshold)
    }

    @JvmStatic
    @JvmOverloads
    fun isDark(
        result: ImageQualityResult,
        darkThreshold: Double = DEFAULT_DARK_THRESHOLD,
        meanLuminanceThreshold: Double = DEFAULT_DARK_MEAN_LUMINANCE_THRESHOLD,
        partialDarkFractionThreshold: Double = DEFAULT_PARTIAL_DARK_FRACTION_THRESHOLD,
        partialDarkMaxMeanLuminance: Double = DEFAULT_PARTIAL_DARK_MAX_MEAN_LUMINANCE,
    ): Boolean {
        val overallDark = result.darkness.compositeDarkness >= darkThreshold
        val lowMeanLuminance = result.darkness.meanLuminance <= meanLuminanceThreshold
        val hasMeaningfulDarkRegion =
            result.darkness.fractionVeryDark >= partialDarkFractionThreshold &&
                result.darkness.meanLuminance <= partialDarkMaxMeanLuminance
        return overallDark || lowMeanLuminance || hasMeaningfulDarkRegion
    }

    @JvmStatic
    @JvmOverloads
    fun isDark(
        bitmap: Bitmap,
        config: AnalysisConfig = AnalysisConfig(),
        darkThreshold: Double = DEFAULT_DARK_THRESHOLD,
        meanLuminanceThreshold: Double = DEFAULT_DARK_MEAN_LUMINANCE_THRESHOLD,
        partialDarkFractionThreshold: Double = DEFAULT_PARTIAL_DARK_FRACTION_THRESHOLD,
        partialDarkMaxMeanLuminance: Double = DEFAULT_PARTIAL_DARK_MAX_MEAN_LUMINANCE,
    ): Boolean {
        return isDark(
            analyze(bitmap, config),
            darkThreshold,
            meanLuminanceThreshold,
            partialDarkFractionThreshold,
            partialDarkMaxMeanLuminance,
        )
    }
}
