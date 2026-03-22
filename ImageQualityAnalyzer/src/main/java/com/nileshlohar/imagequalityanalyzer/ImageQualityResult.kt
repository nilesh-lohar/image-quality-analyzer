package com.nileshlohar.imagequalityanalyzer

/**
 * Raw blur-related metrics. Higher [laplacianVariance], [tenengradMeanEnergy], and
 * [modifiedLaplacianMean] generally indicate a sharper image.
 *
 * [compositeSharpness] is a fused score in **0..1** (higher = sharper), but it is intentionally
 * anchored more heavily to [laplacianVariance] so it tracks the common OpenCV variance-of-Laplacian
 * baseline more closely than a fully custom heuristic score.
 */
data class BlurMetrics(
    val laplacianVariance: Double,
    val tenengradMeanEnergy: Double,
    val modifiedLaplacianMean: Double,
    val compositeSharpness: Double,
)

/**
 * How sharpness is distributed across the image (grid of regions). Use this to detect
 * **selective focus** or **half sharp / half soft** where the global [BlurMetrics.compositeSharpness]
 * can still look "clear" because sharp areas dominate the average.
 */
enum class SpatialSharpnessCategory {
    /** All regions similarly high sharpness. */
    FULL_FRAME_CLEAR,

    /** All regions similarly low sharpness. */
    FULL_FRAME_SOFT,

    /** Strong variation: some regions clearly sharper than others (e.g. one side in focus). */
    MIXED_CLEAR_AND_SOFT,

    /** Average is high but there is noticeable softer patches. */
    MOSTLY_CLEAR,

    /** Average is low but small areas are sharper. */
    MOSTLY_SOFT,

    /** Uneven but not extreme; between the other cases. */
    MODERATE_UNEVEN,
}

data class SpatialBlurMetrics(
    val gridRows: Int,
    val gridCols: Int,
    /** Row-major: index `row * gridCols + col`, each in **0..1** using the same Laplacian-anchored sharpness scale as [BlurMetrics.compositeSharpness]. */
    val regionSharpness: List<Double>,
    /** `max(region) - min(region)`. Larger ⇒ more uneven sharpness across the frame. */
    val sharpnessSpread: Double,
    val category: SpatialSharpnessCategory,
    /** Short explanation for UI or logging. */
    val explanation: String,
)

/**
 * Exposure / darkness metrics on **linear-ish luma** in 0..255.
 *
 * [compositeDarkness] is **0..1** where higher means darker / more underexposed.
 */
data class DarknessMetrics(
    val meanLuminance: Double,
    val stdLuminance: Double,
    val fractionVeryDark: Double,
    val compositeDarkness: Double,
)

data class ImageQualityResult(
    val blur: BlurMetrics,
    val spatial: SpatialBlurMetrics,
    val darkness: DarknessMetrics,
    val analyzedWidth: Int,
    val analyzedHeight: Int,
)
