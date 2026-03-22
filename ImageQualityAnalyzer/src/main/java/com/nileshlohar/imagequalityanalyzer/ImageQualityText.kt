package com.nileshlohar.imagequalityanalyzer

data class ImageQualityMetricLine(
    val label: String,
    val value: String,
)

data class ImageQualitySummary(
    val blurVerdict: String,
    val darknessVerdict: String,
    val spatialCategoryLabel: String,
    val spatialMapSummary: String,
    val spatialExplanation: String,
    val regionSharpnessRows: List<String>,
    val blurMetrics: List<ImageQualityMetricLine>,
    val darknessMetrics: List<ImageQualityMetricLine>,
    val analyzedSizeText: String,
)

object ImageQualityText {

    @JvmStatic
    fun summarize(result: ImageQualityResult): ImageQualitySummary {
        val spatialCategory = spatialCategoryLabel(result.spatial.category)
        return ImageQualitySummary(
            blurVerdict = blurVerdict(result),
            darknessVerdict = darknessVerdict(result),
            spatialCategoryLabel = spatialCategory,
            spatialMapSummary = "$spatialCategory (spread ${"%.3f".format(result.spatial.sharpnessSpread)})",
            spatialExplanation = result.spatial.explanation,
            regionSharpnessRows = regionSharpnessRows(result),
            blurMetrics = listOf(
                ImageQualityMetricLine("compositeSharpness", "%.4f".format(result.blur.compositeSharpness)),
                ImageQualityMetricLine("laplacianVariance", "%.2f".format(result.blur.laplacianVariance)),
                ImageQualityMetricLine("tenengradMeanEnergy", "%.2f".format(result.blur.tenengradMeanEnergy)),
                ImageQualityMetricLine("modifiedLaplacianMean", "%.2f".format(result.blur.modifiedLaplacianMean)),
            ),
            darknessMetrics = listOf(
                ImageQualityMetricLine("compositeDarkness", "%.4f".format(result.darkness.compositeDarkness)),
                ImageQualityMetricLine("meanLuminance", "%.2f".format(result.darkness.meanLuminance)),
                ImageQualityMetricLine("fractionVeryDark", "%.4f".format(result.darkness.fractionVeryDark)),
            ),
            analyzedSizeText = "${result.analyzedWidth} x ${result.analyzedHeight}",
        )
    }

    @JvmStatic
    fun formatReport(label: String, result: ImageQualityResult): String {
        val summary = summarize(result)
        return buildString {
            appendLine(label)
            appendLine()
            appendLine("Summary")
            appendLine("Blur: ${summary.blurVerdict}")
            appendLine("Map: ${summary.spatialMapSummary}")
            appendLine(summary.spatialExplanation)
            appendLine()
            appendLine("Brightness: ${summary.darknessVerdict}")
            appendLine()
            appendLine("Sharpness by Region (${result.spatial.gridRows}x${result.spatial.gridCols})")
            summary.regionSharpnessRows.forEach(::appendLine)
            appendLine()
            appendLine("Blur Metrics")
            appendLine("Higher sharpness means the image is likely clearer.")
            summary.blurMetrics.forEach { metric ->
                appendLine("${metric.label}: ${metric.value}")
            }
            appendLine()
            appendLine("Darkness Metrics")
            appendLine("Higher darkness means the image is likely darker or underexposed.")
            summary.darknessMetrics.forEach { metric ->
                appendLine("${metric.label}: ${metric.value}")
            }
            appendLine()
            appendLine("Analyzed Size")
            appendLine(summary.analyzedSizeText)
        }
    }

    private fun spatialCategoryLabel(category: SpatialSharpnessCategory): String = when (category) {
        SpatialSharpnessCategory.FULL_FRAME_CLEAR -> "Fully clear (even across regions)"
        SpatialSharpnessCategory.FULL_FRAME_SOFT -> "Fully soft / blurry (even across regions)"
        SpatialSharpnessCategory.MIXED_CLEAR_AND_SOFT -> "Mixed: part sharp, part soft (e.g. half in focus)"
        SpatialSharpnessCategory.MOSTLY_CLEAR -> "Mostly clear, small softer patches"
        SpatialSharpnessCategory.MOSTLY_SOFT -> "Mostly soft, small sharper patches"
        SpatialSharpnessCategory.MODERATE_UNEVEN -> "Moderate uneven sharpness"
    }

    /** Whole-image score can look "clear" while a region map shows strong split. */
    private fun blurVerdict(result: ImageQualityResult): String {
        val sharpness = result.blur.compositeSharpness
        val overall = when {
            sharpness >= 0.48 -> "Whole-image average: looks sharp / clear"
            sharpness <= 0.22 -> "Whole-image average: looks blurry or very soft"
            else -> "Whole-image average: medium sharpness"
        }
        val regional = when (result.spatial.category) {
            SpatialSharpnessCategory.MIXED_CLEAR_AND_SOFT ->
                " - but regions differ a lot, so \"average clear\" can hide a half-blur / selective-focus photo."
            SpatialSharpnessCategory.FULL_FRAME_CLEAR,
            SpatialSharpnessCategory.FULL_FRAME_SOFT,
            -> " - matches the region map (similar everywhere)."
            else -> " - see region scores and map category below."
        }
        return overall + regional
    }

    private fun darknessVerdict(result: ImageQualityResult): String {
        val darkness = result.darkness.compositeDarkness
        val mean = result.darkness.meanLuminance
        val darkPart = when {
            darkness >= 0.55 -> "Quite dark / likely underexposed"
            darkness <= 0.28 -> "Well lit / bright enough"
            else -> "Average brightness (not very dark, not very bright)"
        }
        val lumaPart = when {
            mean < 50 -> "Very low average brightness."
            mean < 90 -> "Somewhat dark overall."
            mean > 200 -> "Very bright overall."
            else -> ""
        }
        return if (lumaPart.isEmpty()) darkPart else "$darkPart $lumaPart"
    }

    private fun regionSharpnessRows(result: ImageQualityResult): List<String> {
        val cols = result.spatial.gridCols
        return List(result.spatial.gridRows) { row ->
            val from = row * cols
            val slice = result.spatial.regionSharpness.subList(from, from + cols)
            "Row $row: " + slice.joinToString(" | ") { value -> "%.3f".format(value) }
        }
    }
}
