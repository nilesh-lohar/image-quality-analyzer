package com.nileshlohar.imagequalityanalyzer

/**
 * @param maxSideLength Longest edge after uniform downscale (performance + stability). Same idea as
 *   analyzing a resized image in OpenCV. A slightly larger default preserves more blur detail.
 * @param veryDarkLumaThreshold Pixels below this Rec.601 luma count toward [DarknessMetrics.fractionVeryDark].
 * @param blurFusionWeights Weights for Laplacian variance, Tenengrad, modified Laplacian in [BlurMetrics.compositeSharpness].
 *   The default is intentionally Laplacian-dominant so the public sharpness score stays closer to
 *   the common OpenCV "variance of Laplacian" baseline.
 * @param blurSaturationScales Soft saturation scales for each metric before fusion (tune to your camera pipeline).
 * @param spatialGridRows Number of rows for [SpatialBlurMetrics] (e.g. 2 = top / bottom halves).
 * @param spatialGridCols Number of columns (e.g. 2 = left / right). Use at least 2×2 to detect "half sharp" cases.
 */
data class AnalysisConfig(
    val maxSideLength: Int = 768,
    val veryDarkLumaThreshold: Int = 35,
    val blurFusionWeights: Triple<Double, Double, Double> = Triple(0.78, 0.15, 0.07),
    val blurSaturationScales: Triple<Double, Double, Double> = Triple(150.0, 2_200.0, 55.0),
    val darknessVeryDarkWeight: Double = 0.55,
    val darknessMeanWeight: Double = 0.45,
    val spatialGridRows: Int = 2,
    val spatialGridCols: Int = 2,
) {
    init {
        require(maxSideLength >= 32) { "maxSideLength must be at least 32" }
        require(veryDarkLumaThreshold in 1..254) { "veryDarkLumaThreshold must be in 1..254" }
        val (w1, w2, w3) = blurFusionWeights
        require(w1 >= 0 && w2 >= 0 && w3 >= 0 && (w1 + w2 + w3) > 0) { "blurFusionWeights must be non-negative and sum > 0" }
        require(spatialGridRows >= 1 && spatialGridCols >= 1) { "Spatial grid dimensions must be at least 1" }
        require(spatialGridRows * spatialGridCols <= 64) { "Spatial grid too large" }
    }
}
