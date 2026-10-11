package com.MAD.n152

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

data class ImageQuality(
    val sharpness: Double,
    val brightness: Double,
    val label: String,
    val guidance: String,
    val isGood: Boolean
)

data class DocumentScanResult(
    val bitmap: Bitmap,
    val detected: Boolean,
    val quality: ImageQuality
)

enum class EnhancementMode(val label: String) {
    AUTO("Auto"),
    CLEAN("Clean"),
    BLACK_WHITE("B&W")
}

/** Fully offline document detection, rectification, quality analysis and enhancement. */
object DocumentScanner {
    private const val MAX_ANALYSIS_DIMENSION = 1600
    private const val MAX_OCR_DIMENSION = 3200
    private const val MIN_DOCUMENT_AREA_RATIO = 0.15

    fun prepare(
        input: Bitmap,
        detectDocument: Boolean = true,
        enhancementMode: EnhancementMode = EnhancementMode.AUTO
    ): DocumentScanResult {
        val source = input.copy(Bitmap.Config.ARGB_8888, false)
        val normalized = scaleToMaximum(source, MAX_OCR_DIMENSION)
        val analysis = scaleToMaximum(normalized, MAX_ANALYSIS_DIMENSION)
        val analysisRgba = Mat()
        val fullRgba = Mat()
        var perspectiveCorrected: Mat? = null
        var enhanced: Mat? = null
        var outputBitmap: Bitmap? = null

        return try {
            Utils.bitmapToMat(analysis, analysisRgba)
            val quality = measureQuality(analysisRgba)
            val corners = if (detectDocument) detectCorners(analysisRgba) else null

            Utils.bitmapToMat(normalized, fullRgba)
            perspectiveCorrected = if (corners == null) {
                fullRgba.clone()
            } else {
                val scaleX = normalized.width.toDouble() / analysis.width.toDouble()
                val scaleY = normalized.height.toDouble() / analysis.height.toDouble()
                val fullResolutionCorners = corners.map { point ->
                    Point(point.x * scaleX, point.y * scaleY)
                }.toTypedArray()
                warpPerspective(fullRgba, fullResolutionCorners)
            }

            enhanced = enhanceForOcr(perspectiveCorrected, enhancementMode)
            outputBitmap = Bitmap.createBitmap(
                enhanced.cols(),
                enhanced.rows(),
                Bitmap.Config.ARGB_8888
            ).also { Utils.matToBitmap(enhanced, it) }

            DocumentScanResult(
                bitmap = outputBitmap,
                detected = corners != null,
                quality = quality
            )
        } finally {
            enhanced?.release()
            perspectiveCorrected?.release()
            fullRgba.release()
            analysisRgba.release()
            if (analysis !== normalized) analysis.recycle()
            if (normalized !== source) normalized.recycle()
            if (!source.isRecycled && source !== outputBitmap) source.recycle()
        }
    }

    private fun detectCorners(rgba: Mat): Array<Point>? {
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val closed = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val contours = mutableListOf<MatOfPoint>()

        return try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 55.0, 170.0)
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(
                closed,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea = rgba.width().toDouble() * rgba.height().toDouble()
            contours.asSequence()
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(20)
                .mapNotNull { findDocumentCorners(it, imageArea) }
                .maxByOrNull(::polygonArea)
        } finally {
            contours.forEach(MatOfPoint::release)
            kernel.release()
            hierarchy.release()
            closed.release()
            edges.release()
            blurred.release()
            gray.release()
        }
    }

    private fun findDocumentCorners(contour: MatOfPoint, imageArea: Double): Array<Point>? {
        val area = Imgproc.contourArea(contour)
        if (area < imageArea * MIN_DOCUMENT_AREA_RATIO) return null

        val contour2f = MatOfPoint2f(*contour.toArray())
        val approximation = MatOfPoint2f()
        return try {
            val perimeter = Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approximation, perimeter * 0.02, true)
            val points = approximation.toArray()
            if (points.size != 4) return null
            val polygon = MatOfPoint(*points)
            try {
                if (Imgproc.isContourConvex(polygon)) orderCorners(points) else null
            } finally {
                polygon.release()
            }
        } finally {
            approximation.release()
            contour2f.release()
        }
    }

    private fun measureQuality(rgba: Mat): ImageQuality {
        val gray = Mat()
        val laplacian = Mat()
        val mean = MatOfDouble()
        val standardDeviation = MatOfDouble()
        return try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, standardDeviation)
            val deviation = standardDeviation.toArray().firstOrNull() ?: 0.0
            val sharpness = deviation * deviation
            val brightness = Core.mean(gray).`val`[0]

            when {
                brightness < 55.0 -> ImageQuality(
                    sharpness, brightness, "Too dark", "Add light or move away from shadows", false
                )
                brightness > 220.0 -> ImageQuality(
                    sharpness, brightness, "Too bright", "Reduce glare or change the camera angle", false
                )
                sharpness < 65.0 -> ImageQuality(
                    sharpness, brightness, "Soft image", "Hold steady and move slightly closer", false
                )
                else -> ImageQuality(
                    sharpness, brightness, "Image looks good", "Ready for accurate extraction", true
                )
            }
        } finally {
            standardDeviation.release()
            mean.release()
            laplacian.release()
            gray.release()
        }
    }

    private fun enhanceForOcr(source: Mat, mode: EnhancementMode): Mat {
        val gray = Mat()
        val contrast = Mat()
        val denoised = Mat()
        val blurred = Mat()
        val sharpened = Mat()
        val output = Mat()
        val clahe = Imgproc.createCLAHE(2.2, Size(8.0, 8.0))

        try {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            clahe.apply(gray, contrast)
            // A small median pass removes sensor speckle while preserving text edges and stays
            // responsive on full-resolution phone captures.
            Imgproc.medianBlur(contrast, denoised, 3)

            when (mode) {
                EnhancementMode.BLACK_WHITE -> {
                    Imgproc.adaptiveThreshold(
                        denoised,
                        sharpened,
                        255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY,
                        35,
                        11.0
                    )
                }

                EnhancementMode.AUTO, EnhancementMode.CLEAN -> {
                    val sigma = if (mode == EnhancementMode.CLEAN) 1.35 else 1.05
                    val amount = if (mode == EnhancementMode.CLEAN) 1.50 else 1.32
                    Imgproc.GaussianBlur(denoised, blurred, Size(0.0, 0.0), sigma)
                    Core.addWeighted(denoised, amount, blurred, 1.0 - amount, 0.0, sharpened)
                }
            }

            val padded = Mat()
            try {
                Core.copyMakeBorder(
                    sharpened,
                    padded,
                    18,
                    18,
                    18,
                    18,
                    Core.BORDER_CONSTANT,
                    org.opencv.core.Scalar(255.0)
                )
                Imgproc.cvtColor(padded, output, Imgproc.COLOR_GRAY2RGBA)
            } finally {
                padded.release()
            }
            return output
        } finally {
            clahe.collectGarbage()
            sharpened.release()
            blurred.release()
            denoised.release()
            contrast.release()
            gray.release()
        }
    }

    private fun warpPerspective(source: Mat, corners: Array<Point>): Mat {
        val topWidth = distance(corners[0], corners[1])
        val bottomWidth = distance(corners[3], corners[2])
        val leftHeight = distance(corners[0], corners[3])
        val rightHeight = distance(corners[1], corners[2])
        val outputWidth = max(topWidth, bottomWidth).toInt().coerceAtLeast(1)
        val outputHeight = max(leftHeight, rightHeight).toInt().coerceAtLeast(1)
        val sourcePoints = MatOfPoint2f(*corners)
        val destinationPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outputWidth - 1.0, 0.0),
            Point(outputWidth - 1.0, outputHeight - 1.0),
            Point(0.0, outputHeight - 1.0)
        )
        val transform = Imgproc.getPerspectiveTransform(sourcePoints, destinationPoints)
        val warped = Mat()

        try {
            Imgproc.warpPerspective(
                source,
                warped,
                transform,
                Size(outputWidth.toDouble(), outputHeight.toDouble()),
                Imgproc.INTER_CUBIC,
                Core.BORDER_REPLICATE
            )
            return warped
        } finally {
            transform.release()
            sourcePoints.release()
            destinationPoints.release()
        }
    }

    private fun orderCorners(points: Array<Point>): Array<Point> {
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.minBy { it.y - it.x }
        val bottomLeft = points.maxBy { it.y - it.x }
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun scaleToMaximum(input: Bitmap, maximumDimension: Int): Bitmap {
        val largestDimension = max(input.width, input.height)
        if (largestDimension <= maximumDimension) return input
        val scale = maximumDimension.toDouble() / largestDimension.toDouble()
        return Bitmap.createScaledBitmap(
            input,
            (input.width * scale).toInt().coerceAtLeast(1),
            (input.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun distance(first: Point, second: Point): Double =
        hypot(first.x - second.x, first.y - second.y)

    private fun polygonArea(points: Array<Point>): Double =
        abs(points.indices.sumOf { index ->
            val next = points[(index + 1) % points.size]
            points[index].x * next.y - next.x * points[index].y
        }) / 2.0
}
