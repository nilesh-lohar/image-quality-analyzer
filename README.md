# image-quality-analyzer
Lightweight Android image quality analyzer for blur, partial blur, and darkness detection without OpenCV.

It is designed to stay small and dependency-light while getting closer to common OpenCV-style blur workflows.

## Features

- Pure Android/Kotlin implementation
- No OpenCV dependency
- Global blur metrics:
  - variance of Laplacian
  - Tenengrad mean energy
  - modified Laplacian mean
- Spatial sharpness map to catch partial blur / half-focus cases
- Darkness metrics using mean luminance and dark-pixel fraction
- High-level helper APIs for:
  - `analyze(...)`
  - `summarize(...)`
  - `formatReport(...)`
  - `isBlurry(...)`
  - `isBlurryVarianceOfLaplacian(...)`
  - `isDark(...)`

## Requirements

- Android `minSdk 21+`
- Kotlin / Android project

## Installation

Add JitPack to your root `settings.gradle`, `settings.gradle.kts`, or repository configuration:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Then add the dependency:

```gradle
dependencies {
    implementation "com.github.nilesh-lohar:image-quality-analyzer:<version>"
}
```

Replace `<version>` with the release tag you want to use.

## Basic Usage

The library accepts a `Bitmap`.

```kotlin
import android.graphics.Bitmap
import com.nileshlohar.imagequalityanalyzer.AnalysisConfig
import com.nileshlohar.imagequalityanalyzer.ImageQualityAnalyzer

fun analyzeBitmap(bitmap: Bitmap) {
    val result = ImageQualityAnalyzer.analyze(
        bitmap,
        AnalysisConfig()
    )

    println(result.blur.compositeSharpness)
    println(result.blur.laplacianVariance)
    println(result.darkness.compositeDarkness)
    println(result.spatial.category)
}
```

## Boolean Helpers

### Conservative blur check

This method is intended as a business-friendly blur decision. It can return `true` even when blur is partial.

```kotlin
val result = ImageQualityAnalyzer.analyze(bitmap)
val isBlurry = ImageQualityAnalyzer.isBlurry(result)
```

### Variance-of-Laplacian blur check

This is the simpler raw blur rule based only on Laplacian variance.

```kotlin
val result = ImageQualityAnalyzer.analyze(bitmap)
val isBlurryVol = ImageQualityAnalyzer.isBlurryVarianceOfLaplacian(result)
```

### Darkness check

```kotlin
val result = ImageQualityAnalyzer.analyze(bitmap)
val isDark = ImageQualityAnalyzer.isDark(result)
```

You can also call the helpers directly with a `Bitmap`:

```kotlin
val isBlurry = ImageQualityAnalyzer.isBlurry(bitmap)
val isBlurryVol = ImageQualityAnalyzer.isBlurryVarianceOfLaplacian(bitmap)
val isDark = ImageQualityAnalyzer.isDark(bitmap)
```

## Summary API

If you want a more user-friendly interpreted result:

```kotlin
val result = ImageQualityAnalyzer.analyze(bitmap)
val summary = ImageQualityAnalyzer.summarize(result)

println(summary.blurVerdict)
println(summary.darknessVerdict)
println(summary.spatialMapSummary)
println(summary.regionSharpnessRows)
```

## Text Report API

If you want a ready-made text report:

```kotlin
val result = ImageQualityAnalyzer.analyze(bitmap)
val report = ImageQualityAnalyzer.formatReport("Selected image", result)

println(report)
```

## API Overview

### `ImageQualityAnalyzer.analyze(bitmap, config)`

Returns `ImageQualityResult` with:

- `blur`
- `spatial`
- `darkness`
- `analyzedWidth`
- `analyzedHeight`

### `ImageQualityAnalyzer.summarize(result)`

Returns `ImageQualitySummary` with interpreted values such as:

- blur verdict
- darkness verdict
- spatial map summary
- formatted metric lines

### `ImageQualityAnalyzer.formatReport(label, result)`

Returns a multiline `String` report.

### `ImageQualityAnalyzer.isBlurry(result)`

Conservative blur decision:

- considers overall blur
- considers substantial soft area
- useful for product/business decisions

### `ImageQualityAnalyzer.isBlurryVarianceOfLaplacian(result)`

Raw Laplacian-variance blur decision:

- closer to the common `variance_of_laplacian < threshold` approach
- easier to calibrate against desktop OpenCV experiments

### `ImageQualityAnalyzer.isDark(result)`

Darkness decision based on:

- composite darkness
- mean luminance
- meaningful dark-pixel fraction

## Notes

- The library currently takes a `Bitmap` as input.
- It does not load images from `Uri`, file path, URL, or byte array directly.

## License

Apache License 2.0. See [LICENSE](LICENSE).
