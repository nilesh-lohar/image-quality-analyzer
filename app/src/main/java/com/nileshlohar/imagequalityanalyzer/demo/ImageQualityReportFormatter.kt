package com.nileshlohar.imagequalityanalyzer.demo

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.nileshlohar.imagequalityanalyzer.ImageQualityResult
import com.nileshlohar.imagequalityanalyzer.ImageQualitySummary

internal fun formatReport(
    context: Context,
    label: String,
    result: ImageQualityResult,
    summary: ImageQualitySummary,
    isBlurry: Boolean,
    isBlurryVarianceOfLaplacian: Boolean,
    isDark: Boolean,
): AnnotatedString = buildAnnotatedString {
    fun appendHeading(text: String) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text)
        }
        append("\n\n")
    }

    fun appendMetric(labelText: String, value: String) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(labelText)
        }
        append(value)
        append("\n")
    }

    fun appendItalicLine(text: String) {
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append(text)
        }
        append("\n")
    }

    appendHeading(label)

    appendHeading(context.getString(R.string.report_heading_quick_checks))
    appendMetric(
        context.getString(R.string.report_label_is_blurry),
        if (isBlurry) context.getString(R.string.report_value_yes) else context.getString(R.string.report_value_no),
    )
    appendMetric(
        context.getString(R.string.report_label_is_blurry_variance_of_laplacian),
        if (isBlurryVarianceOfLaplacian) {
            context.getString(R.string.report_value_yes)
        } else {
            context.getString(R.string.report_value_no)
        },
    )
    appendMetric(
        context.getString(R.string.report_label_is_dark),
        if (isDark) context.getString(R.string.report_value_yes) else context.getString(R.string.report_value_no),
    )
    append("\n")

    appendHeading(context.getString(R.string.report_heading_summary))
    appendMetric(context.getString(R.string.report_label_blur), summary.blurVerdict)
    appendMetric(context.getString(R.string.report_label_map), summary.spatialMapSummary)
    appendItalicLine(summary.spatialExplanation)
    append("\n")
    appendMetric(context.getString(R.string.report_label_brightness), summary.darknessVerdict)
    append("\n")

    appendHeading(
        context.getString(
            R.string.report_heading_sharpness_by_region,
            result.spatial.gridRows,
            result.spatial.gridCols,
        ),
    )
    summary.regionSharpnessRows.forEachIndexed { row, line ->
        appendMetric(
            context.getString(R.string.report_label_row, row),
            line.substringAfter(": ").trim(),
        )
    }
    append("\n")

    appendHeading(context.getString(R.string.report_heading_blur_metrics))
    appendItalicLine(context.getString(R.string.report_blur_metrics_hint))
    summary.blurMetrics.forEach { metric ->
        appendMetric("${metric.label}: ", metric.value)
    }
    append("\n")

    appendHeading(context.getString(R.string.report_heading_darkness_metrics))
    appendItalicLine(context.getString(R.string.report_darkness_metrics_hint))
    summary.darknessMetrics.forEach { metric ->
        appendMetric("${metric.label}: ", metric.value)
    }
    append("\n")

    appendHeading(context.getString(R.string.report_heading_analyzed_size))
    appendMetric("", summary.analyzedSizeText)
}
