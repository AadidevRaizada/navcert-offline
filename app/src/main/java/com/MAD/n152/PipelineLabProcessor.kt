package com.MAD.n152

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** The syllabus stages shown in the Pipeline Lab, in teaching order. */
enum class LabStage(val title: String, val unit: String, val summary: String) {
    FUNDAMENTALS(
        "Fundamentals", "Unit 1",
        "A digital image is a matrix of samples. Spatial resolution sets how many samples; gray-level resolution sets how many values each can take."
    ),
    POINT(
        "Point processing", "Unit 2",
        "Each output pixel depends only on the input pixel at the same place: s = T(r). The app's contrast step starts here."
    ),
    HISTOGRAM(
        "Histogram", "Unit 2",
        "Equalization maps gray levels through the cumulative histogram. The app uses CLAHE (clip 2.2, 8 x 8 tiles) so shadows do not wash out text."
    ),
    FILTERING(
        "Filtering", "Units 2 and 3",
        "Neighbourhood filters smooth noise or sharpen strokes. The Fourier spectrum shows the same page in the frequency domain."
    ),
    DCT(
        "DCT compression", "Unit 3",
        "JPEG splits the page into 8 x 8 blocks, takes the DCT and quantises it. Most energy sits in a few low-frequency coefficients."
    ),
    EDGES(
        "Edges", "Unit 5",
        "First- and second-derivative operators find intensity changes. The app runs Canny (55 / 170) to find the page border."
    ),
    MORPHOLOGY(
        "Morphology", "Unit 4",
        "Binary shapes grow, shrink and get cleaned with a 3 x 3 structuring element. The app closes Canny gaps with a 5 x 5 close."
    ),
    SEGMENTATION(
        "Segmentation", "Unit 6",
        "Thresholding splits ink from paper. Otsu picks T from the histogram; adaptive thresholding (block 35, C 11) is the app's B&W mode."
    ),
    COLOUR(
        "Colour", "Unit 7",
        "The same page in RGB, HSV and YUV. Hue ranges isolate blue ink and the red stamp without touching the black print."
    ),
    GEOMETRY(
        "Page geometry", "Units 1 and 5",
        "Edges, contours and a four-corner polygon give a perspective transform that flattens the photographed page."
    ),
    MOTION(
        "Steady capture", "Unit 8",
        "Block matching between two preview frames measures camera shake. A still phone gives near-zero vectors, so the app can wait for a steady frame."
    )
}

data class LabPanel(val label: String, val bitmap: Bitmap, val wide: Boolean = false)

data class LabHistogram(val label: String, val counts: IntArray, val marker: Int? = null)

data class LabStageResult(
    val panels: List<LabPanel>,
    val metrics: List<Pair<String, String>>,
    val histograms: List<LabHistogram> = emptyList(),
    val note: String = ""
)

data class LabParameters(val gamma: Double = 0.6, val quality: Int = 50)

/** Prepared input for every stage: the photo, its detected page corners and the flattened page. */
class LabInput(
    val sourceLabel: String,
    val original: Bitmap,
    val sourceBytes: Long?,
    val corners: Array<Point>?,
    val document: Bitmap,
    val gray: GrayImage
)

/** OpenCV-backed stage computations. Pure algorithms live in [IvpOperations]. */
object PipelineLabProcessor {
    const val SAMPLE_ASSET = "lab/sample_certificate_photo.jpg"
    private const val ANALYSIS_DIMENSION = 1600
    private const val DOCUMENT_DIMENSION = 900

    fun loadSample(context: Context): Pair<Bitmap, Long> {
        val bytes = context.assets.open(SAMPLE_ASSET).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Sample certificate could not be decoded")
        return bitmap to bytes.size.toLong()
    }

    fun prepare(source: Bitmap, label: String, sourceBytes: Long?): LabInput {
        val original = scaleTo(source.copy(Bitmap.Config.ARGB_8888, false), ANALYSIS_DIMENSION)
        val rgba = Mat()
        Utils.bitmapToMat(original, rgba)
        try {
            val corners = DocumentScanner.detectCorners(rgba)
            val flattened = if (corners != null) DocumentScanner.warpPerspective(rgba, corners) else rgba.clone()
            val documentFull = matToBitmap(flattened)
            flattened.release()
            val document = scaleTo(documentFull, DOCUMENT_DIMENSION)
            return LabInput(label, original, sourceBytes, corners, document, toGray(document))
        } finally {
            rgba.release()
        }
    }

    fun compute(stage: LabStage, input: LabInput, parameters: LabParameters): LabStageResult = when (stage) {
        LabStage.FUNDAMENTALS -> fundamentals(input)
        LabStage.POINT -> pointProcessing(input, parameters.gamma)
        LabStage.HISTOGRAM -> histogram(input)
        LabStage.FILTERING -> filtering(input)
        LabStage.DCT -> dct(input, parameters.quality)
        LabStage.EDGES -> edges(input)
        LabStage.MORPHOLOGY -> morphology(input)
        LabStage.SEGMENTATION -> segmentation(input)
        LabStage.COLOUR -> colour(input)
        LabStage.GEOMETRY -> geometry(input)
        LabStage.MOTION -> motion(input)
    }

    // ---------------- Unit 1 ----------------

    private fun fundamentals(input: LabInput): LabStageResult {
        val gray = input.gray
        val png = encodedSize(input.document, Bitmap.CompressFormat.PNG, 100)
        val jpeg = encodedSize(input.document, Bitmap.CompressFormat.JPEG, 90)
        val raw = input.document.width.toLong() * input.document.height * 3
        val panels = listOf(
            LabPanel("Colour, 24-bit RGB", input.document),
            LabPanel("Gray, 256 levels (8 bit)", gray.toBitmap()),
            LabPanel("16 gray levels (4 bit)", IvpOperations.requantize(gray, 16).toBitmap()),
            LabPanel("8 gray levels (3 bit)", IvpOperations.requantize(gray, 8).toBitmap()),
            LabPanel("Spatial 1/4: every 4th sample", IvpOperations.downsampleReplicate(gray, 4).toBitmap()),
            LabPanel("Spatial 1/8: every 8th sample", IvpOperations.downsampleReplicate(gray, 8).toBitmap())
        )
        val metrics = buildList {
            add("Source" to input.sourceLabel)
            add("Photo resolution" to "${input.original.width} x ${input.original.height} px")
            add("Flattened page" to "${input.document.width} x ${input.document.height} px")
            add("Channels" to "R, G, B + alpha (ARGB_8888)")
            add("Bit depth" to "8 bits per channel, 24 bits per colour pixel")
            add("Raw RGB size" to kib(raw))
            add("PNG (lossless)" to "${kib(png)}  (${ratio(raw, png)} : 1)")
            add("JPEG quality 90" to "${kib(jpeg)}  (${ratio(raw, jpeg)} : 1)")
            input.sourceBytes?.let { add("Source file" to kib(it)) }
        }
        return LabStageResult(
            panels, metrics,
            note = "PNG keeps every value exactly. JPEG discards detail the eye barely sees, which is why the page shrinks far more."
        )
    }

    // ---------------- Unit 2: point processing ----------------

    private fun pointProcessing(input: LabInput, gamma: Double): LabStageResult {
        val gray = input.gray
        val hist = IvpOperations.histogram(gray)
        val r1 = IvpOperations.percentile(hist, 0.02)
        val r2 = max(r1 + 1, IvpOperations.percentile(hist, 0.98))
        val stretched = gray.applyLut(IvpOperations.contrastStretchLut(r1, 0, r2, 255))
        val gammaImage = gray.applyLut(IvpOperations.gammaLut(gamma))
        val sliced = gray.applyLut(IvpOperations.intensitySliceLut(0, 110, highlight = 255, preserve = false, background = 30))
        val panels = listOf(
            LabPanel("Input gray", gray.toBitmap()),
            LabPanel("Negative  s = 255 - r", gray.applyLut(IvpOperations.negativeLut()).toBitmap()),
            LabPanel("Contrast stretch $r1..$r2 to 0..255", stretched.toBitmap()),
            LabPanel("Log  s = c log(1 + r)", gray.applyLut(IvpOperations.logLut()).toBitmap()),
            LabPanel("Gamma  s = 255 (r/255)^${fmt(gamma, 2)}", gammaImage.toBitmap()),
            LabPanel("Intensity slice 0..110 (ink)", sliced.toBitmap())
        )
        val metrics = listOf(
            "Stretch breakpoints" to "r1 = $r1 (2nd percentile), r2 = $r2 (98th)",
            "Mean before / after stretch" to "${fmt(IvpOperations.mean(gray), 1)} / ${fmt(IvpOperations.mean(stretched), 1)}",
            "Gamma" to fmt(gamma, 2),
            "Mean after gamma" to fmt(IvpOperations.mean(gammaImage), 1),
            "Pixels in ink slice" to pct(sliced.pixels.count { it == 255 }, sliced.pixels.size)
        )
        return LabStageResult(
            panels, metrics,
            note = "Gamma below 1 brightens shadows; above 1 darkens faded print. Move the slider to compare."
        )
    }

    // ---------------- Unit 2: histogram ----------------

    private fun histogram(input: LabInput): LabStageResult {
        val gray = input.gray
        val equalized = IvpOperations.equalize(gray)
        val clahe = withMat(gray) { src ->
            val out = Mat()
            val operator = Imgproc.createCLAHE(2.2, Size(8.0, 8.0))
            operator.apply(src, out)
            operator.collectGarbage()
            out.toGrayImage().also { out.release() }
        }
        val images = listOf("Input" to gray, "Global equalization" to equalized, "CLAHE 2.2, 8 x 8" to clahe)
        val metrics = images.map { (label, image) ->
            val hist = IvpOperations.histogram(image)
            label to "mean ${fmt(IvpOperations.mean(image), 1)}, std ${fmt(IvpOperations.standardDeviation(image), 1)}, " +
                "range ${IvpOperations.percentile(hist, 0.01)}..${IvpOperations.percentile(hist, 0.99)}, " +
                "entropy ${fmt(IvpOperations.entropy(hist), 2)} bit"
        }
        return LabStageResult(
            panels = images.map { (label, image) -> LabPanel(label, image.toBitmap()) },
            metrics = metrics,
            histograms = images.map { (label, image) -> LabHistogram(label, IvpOperations.histogram(image)) },
            note = "Global equalization flattens the whole histogram and amplifies paper texture. CLAHE limits the gain per tile, so local contrast rises without blowing out the shadowed corner."
        )
    }

    // ---------------- Units 2 and 3: spatial and frequency filtering ----------------

    private fun filtering(input: LabInput): LabStageResult {
        val clean = input.gray
        val random = Random(152)
        val noisy = clean.copy()
        for (i in noisy.pixels.indices) {
            val r = random.nextDouble()
            if (r < 0.025) noisy.pixels[i] = 0 else if (r < 0.05) noisy.pixels[i] = 255
        }
        val noisyMat = noisy.toMat()
        val cleanMat = clean.toMat()
        val mean = Mat().also { Imgproc.blur(noisyMat, it, Size(3.0, 3.0)) }.toGrayImageAndRelease()
        val gaussian = Mat().also { Imgproc.GaussianBlur(noisyMat, it, Size(5.0, 5.0), 1.0) }.toGrayImageAndRelease()
        val median = Mat().also { Imgproc.medianBlur(noisyMat, it, 3) }.toGrayImageAndRelease()
        val unsharp = run {
            val blurred = Mat()
            val out = Mat()
            Imgproc.GaussianBlur(cleanMat, blurred, Size(0.0, 0.0), 1.05)
            Core.addWeighted(cleanMat, 1.32, blurred, 1.0 - 1.32, 0.0, out)
            blurred.release()
            out.toGrayImageAndRelease()
        }
        val laplacianSharpened = run {
            val kernel = Mat(3, 3, CvType.CV_32F)
            kernel.put(0, 0, 0.0, -1.0, 0.0, -1.0, 5.0, -1.0, 0.0, -1.0, 0.0)
            val out = Mat()
            Imgproc.filter2D(cleanMat, out, -1, kernel)
            kernel.release()
            out.toGrayImageAndRelease()
        }
        noisyMat.release()

        // Frequency domain on a smaller copy keeps the DFT quick.
        val small = Mat()
        val scale = min(1.0, 512.0 / max(clean.width, clean.height))
        Imgproc.resize(cleanMat, small, Size(clean.width * scale, clean.height * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        cleanMat.release()
        val (spectrum, lowPass) = frequencyDomain(small, cutoff = 30.0)
        small.release()

        val panels = listOf(
            LabPanel("5% salt-and-pepper noise", noisy.toBitmap()),
            LabPanel("Mean 3 x 3", mean.toBitmap()),
            LabPanel("Gaussian 5 x 5, sigma 1", gaussian.toBitmap()),
            LabPanel("Median 3 x 3", median.toBitmap()),
            LabPanel("Unsharp mask (app: sigma 1.05, x1.32)", unsharp.toBitmap()),
            LabPanel("Laplacian sharpening f - Lap(f)", laplacianSharpened.toBitmap()),
            LabPanel("DFT log-magnitude, centred", spectrum.toBitmap()),
            LabPanel("Gaussian low-pass, D0 = 30", lowPass.toBitmap())
        )
        val metrics = listOf(
            "PSNR noisy vs clean" to db(IvpOperations.psnr(clean, noisy)),
            "PSNR mean 3 x 3" to db(IvpOperations.psnr(clean, mean)),
            "PSNR Gaussian 5 x 5" to db(IvpOperations.psnr(clean, gaussian)),
            "PSNR median 3 x 3" to db(IvpOperations.psnr(clean, median)),
            "DFT size" to "${spectrum.width} x ${spectrum.height}"
        )
        return LabStageResult(
            panels, metrics,
            note = "The median filter removes impulse noise because a lone 0 or 255 is never the middle value of its 3 x 3 neighbourhood. Averaging only spreads it. In the centred spectrum most energy sits near the middle, the low frequencies; the Gaussian low-pass keeps only that region, so fine strokes blur."
        )
    }

    private fun frequencyDomain(gray8: Mat, cutoff: Double): Pair<GrayImage, GrayImage> {
        val floatImage = Mat()
        gray8.convertTo(floatImage, CvType.CV_32F)
        val complex = Mat()
        Core.dft(floatImage, complex, Core.DFT_COMPLEX_OUTPUT)
        val planes = ArrayList<Mat>()
        Core.split(complex, planes)
        val magnitude = Mat()
        Core.magnitude(planes[0], planes[1], magnitude)
        Core.add(magnitude, Scalar(1.0), magnitude)
        Core.log(magnitude, magnitude)
        val shifted = fftShift(magnitude)
        val spectrum8 = Mat()
        Core.normalize(shifted, spectrum8, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)

        val rows = complex.rows()
        val cols = complex.cols()
        val filter = FloatArray(rows * cols)
        for (u in 0 until rows) {
            val du = min(u, rows - u).toDouble()
            for (v in 0 until cols) {
                val dv = min(v, cols - v).toDouble()
                filter[u * cols + v] = exp(-(du * du + dv * dv) / (2 * cutoff * cutoff)).toFloat()
            }
        }
        val h = Mat(rows, cols, CvType.CV_32F)
        h.put(0, 0, filter)
        val h2 = Mat()
        Core.merge(listOf(h, h), h2)
        val filtered = Mat()
        Core.multiply(complex, h2, filtered)
        val restored = Mat()
        Core.idft(filtered, restored, Core.DFT_SCALE or Core.DFT_REAL_OUTPUT)
        val restored8 = Mat()
        restored.convertTo(restored8, CvType.CV_8U)

        val result = spectrum8.toGrayImage() to restored8.toGrayImage()
        listOf(floatImage, complex, magnitude, shifted, spectrum8, h, h2, filtered, restored, restored8).forEach(Mat::release)
        planes.forEach(Mat::release)
        return result
    }

    private fun fftShift(source: Mat): Mat {
        val out = source.clone()
        val cx = source.cols() / 2
        val cy = source.rows() / 2
        val rows = source.rows()
        val cols = source.cols()
        // Swap quadrants so the zero frequency sits in the centre (works for odd sizes too).
        for (y in 0 until rows) {
            val row = FloatArray(cols)
            source.get(y, 0, row)
            val shiftedRow = FloatArray(cols) { row[(it - cx + cols) % cols] }
            out.put((y + cy) % rows, 0, shiftedRow)
        }
        return out
    }

    // ---------------- Unit 3: DCT ----------------

    private fun dct(input: LabInput, quality: Int): LabStageResult {
        val gray = input.gray
        val quantised = IvpOperations.dctQuantize(gray, quality)
        val topK = IvpOperations.dctTopK(gray, 6)
        val cropped = IvpOperations.crop(gray, 0, 0, quantised.reconstructed.width, quantised.reconstructed.height)
        val (x0, y0, w, h) = detailRect(cropped)
        val zoom = 2
        val detail = IvpOperations.crop(cropped, x0, y0, w, h)
        val detailQ = IvpOperations.crop(quantised.reconstructed, x0, y0, w, h)
        val detailK = IvpOperations.crop(topK.reconstructed, x0, y0, w, h)
        val error = IvpOperations.absDifference(detail, detailQ, gain = 4)
        val table = listOf(10, 50, 90).map { q -> q to IvpOperations.dctQuantize(gray, q) }
        val panels = listOf(
            LabPanel("Original detail", detail.toBitmap(zoom)),
            LabPanel("JPEG table, quality $quality", detailQ.toBitmap(zoom)),
            LabPanel("Error |f - f'| x 4", error.toBitmap(zoom)),
            LabPanel("Top 6 of 64 coefficients per block", detailK.toBitmap(zoom)),
            LabPanel("Block DCT log-magnitude", IvpOperations.dctMagnitudeImage(gray).toBitmap()),
            LabPanel("Reconstructed page, quality $quality", quantised.reconstructed.toBitmap())
        )
        val metrics = buildList {
            add("Blocks" to "${quantised.reconstructed.width / 8} x ${quantised.reconstructed.height / 8} of 8 x 8")
            add("Quality $quality: PSNR" to db(quantised.psnr))
            add("Quality $quality: coefficients kept" to "${fmt(quantised.keptPercent, 1)} % non-zero")
            add("Top 6 per block: PSNR" to "${db(topK.psnr)} at ${fmt(topK.keptPercent, 1)} % kept")
            table.forEach { (q, r) -> add("Quality $q" to "${db(r.psnr)}, ${fmt(r.keptPercent, 1)} % kept") }
        }
        return LabStageResult(
            panels, metrics,
            note = "Each coefficient is divided by the JPEG luminance table (scaled by quality) and rounded. High frequencies get large divisors and mostly become zero, which is what an entropy coder then packs tightly."
        )
    }

    // ---------------- Unit 5: edges ----------------

    private fun edges(input: LabInput): LabStageResult {
        val gray = input.gray.toMat()
        val gx = Mat()
        val gy = Mat()
        val magnitude = Mat()
        Imgproc.Sobel(gray, gx, CvType.CV_32F, 1, 0, 3)
        Imgproc.Sobel(gray, gy, CvType.CV_32F, 0, 1, 3)
        Core.magnitude(gx, gy, magnitude)
        val sobel = normalize8(magnitude)

        val px = Mat(3, 3, CvType.CV_32F).apply { put(0, 0, -1.0, 0.0, 1.0, -1.0, 0.0, 1.0, -1.0, 0.0, 1.0) }
        val py = Mat(3, 3, CvType.CV_32F).apply { put(0, 0, -1.0, -1.0, -1.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0) }
        Imgproc.filter2D(gray, gx, CvType.CV_32F, px)
        Imgproc.filter2D(gray, gy, CvType.CV_32F, py)
        Core.magnitude(gx, gy, magnitude)
        val prewitt = normalize8(magnitude)

        val laplacian = Mat()
        Imgproc.Laplacian(gray, laplacian, CvType.CV_32F, 3)
        Core.absdiff(laplacian, Scalar(0.0), laplacian)
        val lap = normalize8(laplacian)

        val smoothed = Mat()
        Imgproc.GaussianBlur(gray, smoothed, Size(0.0, 0.0), 2.0)
        Imgproc.Laplacian(smoothed, laplacian, CvType.CV_32F, 3)
        Core.absdiff(laplacian, Scalar(0.0), laplacian)
        val log = normalize8(laplacian)

        Imgproc.GaussianBlur(gray, smoothed, Size(5.0, 5.0), 0.0)
        val canny = Mat()
        Imgproc.Canny(smoothed, canny, 55.0, 170.0)
        val cannyImage = canny.toGrayImage()
        listOf(gray, gx, gy, magnitude, px, py, laplacian, smoothed, canny).forEach(Mat::release)

        val panels = listOf(
            LabPanel("Input gray", input.gray.toBitmap()),
            LabPanel("Sobel |G|", sobel.toBitmap()),
            LabPanel("Prewitt |G|", prewitt.toBitmap()),
            LabPanel("Laplacian |Lap f|", lap.toBitmap()),
            LabPanel("LoG, sigma 2", log.toBitmap()),
            LabPanel("Canny 55 / 170 (app)", cannyImage.toBitmap())
        )
        val metrics = listOf(
            "Canny edge pixels" to pct(cannyImage.pixels.count { it != 0 }, cannyImage.pixels.size),
            "Mean Sobel magnitude (0-255 scale)" to fmt(IvpOperations.mean(sobel), 1),
            "Canny thresholds" to "low 55, high 170, after 5 x 5 Gaussian"
        )
        return LabStageResult(
            panels, metrics,
            note = "Sobel and Prewitt estimate the first derivative; Laplacian responds to the second derivative and to noise, so LoG smooths first. Canny adds non-maximum suppression and hysteresis, giving thin, connected edges."
        )
    }

    // ---------------- Unit 4: morphology ----------------

    private fun morphology(input: LabInput): LabStageResult {
        val gray = input.gray
        val band = IvpOperations.crop(
            gray, (gray.width * 0.08).toInt(), (gray.height * 0.24).toInt(),
            (gray.width * 0.84).toInt(), (gray.height * 0.13).toInt()
        )
        val t = IvpOperations.otsuThreshold(IvpOperations.histogram(band))
        val a = band.map { if (it <= t) 255 else 0 }
        val dilated = IvpOperations.dilate(a)
        val eroded = IvpOperations.erode(a)
        val opened = IvpOperations.dilate(IvpOperations.erode(a))
        val closed = IvpOperations.erode(IvpOperations.dilate(a))
        val boundary = IvpOperations.boundary(a)
        val zoom = 2
        val images = listOf(
            "A: ink after Otsu (T = $t)" to a,
            "Dilation A + B" to dilated,
            "Erosion A - B" to eroded,
            "Opening (A - B) + B" to opened,
            "Closing (A + B) - B" to closed,
            "Boundary A - (A - B)" to boundary
        )
        return LabStageResult(
            panels = images.map { (label, image) -> LabPanel(label, image.toBitmap(zoom), wide = true) },
            metrics = images.map { (label, image) ->
                listOf("A", "Dilation", "Erosion", "Opening", "Closing", "Boundary")[images.indexOfFirst { it.first == label }] to "${image.pixels.count { it != 0 }} foreground pixels"
            } + ("Structuring element B" to "3 x 3 square, applied in pure Kotlin"),
            note = "Dilation thickens strokes and closes gaps; erosion thins them and deletes specks. Opening removes noise smaller than B; closing fills small holes. The app uses a 5 x 5 closing to join broken page edges before contour search."
        )
    }

    // ---------------- Unit 6: segmentation ----------------

    private fun segmentation(input: LabInput): LabStageResult {
        val gray = input.gray
        val hist = IvpOperations.histogram(gray)
        val otsu = IvpOperations.otsuThreshold(hist)
        val grayMat = gray.toMat()
        val opencvBinary = Mat()
        val opencvT = Imgproc.threshold(grayMat, opencvBinary, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        opencvBinary.release()
        val adaptiveMat = Mat()
        Imgproc.adaptiveThreshold(grayMat, adaptiveMat, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 35, 11.0)
        val adaptive = adaptiveMat.toGrayImageAndRelease()
        grayMat.release()

        val fixed = IvpOperations.threshold(gray, 128)
        val otsuBinary = IvpOperations.threshold(gray, otsu)
        val ink = otsuBinary.map { 255 - it }
        val four = IvpOperations.connectedComponents(ink, 4)
        val eight = IvpOperations.connectedComponents(ink, 8)
        val adaptiveInk = IvpOperations.connectedComponents(adaptive.map { 255 - it }, 8)

        val panels = listOf(
            LabPanel("Global fixed T = 128", fixed.toBitmap()),
            LabPanel("Otsu T = $otsu", otsuBinary.toBitmap()),
            LabPanel("Adaptive Gaussian, block 35, C 11", adaptive.toBitmap()),
            LabPanel("8-connected components (${eight.count})", labelsBitmap(eight, gray.width, gray.height))
        )
        val metrics = listOf(
            "Otsu T (Kotlin)" to otsu.toString(),
            "Otsu T (OpenCV check)" to fmt(opencvT, 0),
            "Ink pixels, fixed 128" to pct(fixed.pixels.count { it == 0 }, fixed.pixels.size),
            "Ink pixels, Otsu" to pct(otsuBinary.pixels.count { it == 0 }, otsuBinary.pixels.size),
            "Ink pixels, adaptive" to pct(adaptive.pixels.count { it == 0 }, adaptive.pixels.size),
            "Components, 4-connected" to four.count.toString(),
            "Components, 8-connected" to eight.count.toString(),
            "Components, adaptive (8)" to adaptiveInk.count.toString()
        )
        return LabStageResult(
            panels, metrics,
            histograms = listOf(LabHistogram("Gray histogram with Otsu threshold", hist, marker = otsu)),
            note = "Otsu maximises the between-class variance of paper and ink. One global T fails where shadow darkens the paper, so the app's B&W mode thresholds each pixel against its own 35 x 35 Gaussian-weighted neighbourhood. 8-connectivity joins diagonal stroke pixels, so it finds fewer components than 4-connectivity."
        )
    }

    private fun labelsBitmap(labels: ComponentLabels, width: Int, height: Int): Bitmap {
        val palette = IntArray(labels.count + 1) { index ->
            if (index == 0) Color.WHITE else {
                val random = Random(index * 7919)
                Color.rgb(random.nextInt(20, 200), random.nextInt(20, 200), random.nextInt(20, 200))
            }
        }
        return Bitmap.createBitmap(IntArray(width * height) { palette[labels.labels[it]] }, width, height, Bitmap.Config.ARGB_8888)
    }

    // ---------------- Unit 7: colour ----------------

    private fun colour(input: LabInput): LabStageResult {
        val rgba = Mat()
        Utils.bitmapToMat(input.document, rgba)
        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        val hsv = Mat()
        val yuv = Mat()
        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
        Imgproc.cvtColor(rgb, yuv, Imgproc.COLOR_RGB2YUV)
        val rgbPlanes = ArrayList<Mat>().also { Core.split(rgb, it) }
        val hsvPlanes = ArrayList<Mat>().also { Core.split(hsv, it) }
        val yuvPlanes = ArrayList<Mat>().also { Core.split(yuv, it) }

        val blueMask = Mat()
        Core.inRange(hsv, Scalar(100.0, 70.0, 40.0), Scalar(135.0, 255.0, 255.0), blueMask)
        val redLow = Mat()
        val redHigh = Mat()
        val redMask = Mat()
        Core.inRange(hsv, Scalar(0.0, 70.0, 60.0), Scalar(10.0, 255.0, 255.0), redLow)
        Core.inRange(hsv, Scalar(165.0, 70.0, 60.0), Scalar(180.0, 255.0, 255.0), redHigh)
        Core.bitwise_or(redLow, redHigh, redMask)
        val blue = sliceBitmap(rgba, blueMask)
        val red = sliceBitmap(rgba, redMask)
        val total = blueMask.total().toInt()
        val bluePixels = Core.countNonZero(blueMask)
        val redPixels = Core.countNonZero(redMask)

        val panels = listOf(
            LabPanel("R", rgbPlanes[0].toGrayImage().toBitmap()),
            LabPanel("G", rgbPlanes[1].toGrayImage().toBitmap()),
            LabPanel("B", rgbPlanes[2].toGrayImage().toBitmap()),
            LabPanel("H (hue, 0-180)", hsvPlanes[0].toGrayImage().toBitmap()),
            LabPanel("S (saturation)", hsvPlanes[1].toGrayImage().toBitmap()),
            LabPanel("V (value)", hsvPlanes[2].toGrayImage().toBitmap()),
            LabPanel("Y (luma)", yuvPlanes[0].toGrayImage().toBitmap()),
            LabPanel("U (blue - luma)", yuvPlanes[1].toGrayImage().toBitmap()),
            LabPanel("Colour slice: blue ink", blue),
            LabPanel("Colour slice: red stamp", red)
        )
        val metrics = listOf(
            "Blue slice" to "H 100-135, S >= 70, V >= 40: ${pct(bluePixels, total)} of pixels",
            "Red slice" to "H 0-10 or 165-180, S >= 70, V >= 60: ${pct(redPixels, total)}",
            "YUV" to "Y = 0.299 R + 0.587 G + 0.114 B; U and V carry colour difference"
        )
        (rgbPlanes + hsvPlanes + yuvPlanes).forEach(Mat::release)
        listOf(rgba, rgb, hsv, yuv, blueMask, redLow, redHigh, redMask).forEach(Mat::release)
        return LabStageResult(
            panels, metrics,
            note = "Black print has almost no saturation, so a hue range alone separates the blue headings and the red stamp. Y is what OCR mostly sees; colour lives in U and V, which JPEG and video codecs subsample."
        )
    }

    private fun sliceBitmap(rgba: Mat, mask: Mat): Bitmap {
        val out = Mat(rgba.size(), rgba.type(), Scalar(255.0, 255.0, 255.0, 255.0))
        rgba.copyTo(out, mask)
        return matToBitmap(out).also { out.release() }
    }

    // ---------------- Geometry ----------------

    private fun geometry(input: LabInput): LabStageResult {
        val rgba = Mat()
        Utils.bitmapToMat(input.original, rgba)
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val closed = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(blurred, edges, 55.0, 170.0)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
        val quality = DocumentScanner.measureQuality(rgba)
        val documentMat = Mat().also { Utils.bitmapToMat(input.document, it) }
        val enhancedMat = DocumentScanner.enhanceForOcr(documentMat, EnhancementMode.AUTO)
        val enhanced = matToBitmap(enhancedMat)
        val closedImage = closed.toGrayImage()
        listOf(rgba, gray, blurred, edges, closed, kernel, documentMat, enhancedMat).forEach(Mat::release)

        val corners = input.corners
        val annotated = input.original.copy(Bitmap.Config.ARGB_8888, true)
        if (corners != null) {
            val canvas = Canvas(annotated)
            val stroke = max(4f, annotated.width / 220f)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(31, 75, 216); style = Paint.Style.STROKE; strokeWidth = stroke
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 40, 40) }
            val path = Path().apply {
                moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
                for (i in 1..3) lineTo(corners[i].x.toFloat(), corners[i].y.toFloat())
                close()
            }
            canvas.drawPath(path, line)
            corners.forEach { canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), stroke * 3f, dot) }
        }

        val panels = listOf(
            LabPanel(if (corners != null) "Detected page corners" else "No page quadrilateral found", annotated),
            LabPanel("Canny 55/170 + 5 x 5 closing", closedImage.toBitmap()),
            LabPanel("Perspective-warped page", input.document),
            LabPanel("App enhancement (CLAHE, median, unsharp)", enhanced)
        )
        val imageArea = input.original.width.toDouble() * input.original.height
        val metrics = buildList {
            if (corners != null) {
                val names = listOf("Top-left", "Top-right", "Bottom-right", "Bottom-left")
                corners.forEachIndexed { i, p -> add(names[i] to "(${p.x.roundToInt()}, ${p.y.roundToInt()})") }
                val area = polygonArea(corners)
                add("Page area" to "${fmt(100.0 * area / imageArea, 1)} % of frame (minimum 15 %)")
                val tl = corners[0]
                val br = corners[2]
                val x1 = tl.x.roundToInt(); val y1 = tl.y.roundToInt(); val x2 = br.x.roundToInt(); val y2 = br.y.roundToInt()
                add(
                    "Diagonal TL to BR" to "Euclidean ${fmt(IvpOperations.euclideanDistance(x1, y1, x2, y2), 1)}, " +
                        "city-block ${IvpOperations.cityBlockDistance(x1, y1, x2, y2)}, " +
                        "chessboard ${IvpOperations.chessboardDistance(x1, y1, x2, y2)} px"
                )
            } else {
                add("Corners" to "Not found; the whole frame is used")
            }
            add("Flattened size" to "${input.document.width} x ${input.document.height} px (display copy)")
            add("Sharpness (Laplacian variance)" to "${fmt(quality.sharpness, 1)}  (blurry below 65)")
            add("Brightness" to "${fmt(quality.brightness, 1)}  (dark below 55, bright above 220)")
            add("Quality verdict" to quality.label)
        }
        return LabStageResult(
            panels, metrics,
            note = "The largest convex four-sided contour covering at least 15 % of the frame is taken as the page. Its corners map to a rectangle through a 3 x 3 homography, removing the perspective of a hand-held photo."
        )
    }

    private fun polygonArea(points: Array<Point>): Double =
        abs(points.indices.sumOf { i ->
            val next = points[(i + 1) % points.size]
            points[i].x * next.y - next.x * points[i].y
        }) / 2.0

    // ---------------- Unit 8: motion estimation ----------------

    private fun motion(input: LabInput): LabStageResult {
        // Reference frame: the page at preview size. Current frame: the same scene after a small hand movement
        // (translation plus a slight rotation) with sensor noise, as two consecutive preview frames would differ.
        val referenceMat = Mat()
        val grayMat = input.gray.toMat()
        val scale = 384.0 / input.gray.width
        Imgproc.resize(grayMat, referenceMat, Size(384.0, (input.gray.height * scale).roundToInt().toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        grayMat.release()
        val reference = referenceMat.toGrayImage()
        val rotation = Imgproc.getRotationMatrix2D(Point(reference.width / 2.0, reference.height / 2.0), 0.4, 1.0)
        rotation.put(0, 2, rotation.get(0, 2)[0] + 4.0)
        rotation.put(1, 2, rotation.get(1, 2)[0] - 3.0)
        val currentMat = Mat()
        Imgproc.warpAffine(referenceMat, currentMat, rotation, referenceMat.size(), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
        referenceMat.release()
        rotation.release()
        val noise = Random(8)
        val current = currentMat.toGrayImageAndRelease().map { (it + noise.nextInt(-2, 3)).coerceIn(0, 255) }

        val methods = listOf(IvpOperations.METHOD_EXHAUSTIVE, IvpOperations.METHOD_THREE_STEP, IvpOperations.METHOD_TWO_D_LOG)
        val timings = HashMap<String, Double>()
        val summaries = methods.map { method ->
            val start = System.nanoTime()
            val summary = IvpOperations.estimateMotion(reference, current, method, blockSize = 16, range = 7)
            timings[method] = (System.nanoTime() - start) / 1e6
            summary
        }
        val exhaustive = summaries[0]
        val predicted = IvpOperations.motionCompensate(reference, exhaustive.vectors)
        val residualBefore = IvpOperations.meanAbsoluteDifference(current, reference)
        val residualAfter = IvpOperations.meanAbsoluteDifference(current, predicted)

        // Search pattern for one textured block near the centre.
        val bx = (reference.width / 2) / 16 * 16
        val by = (reference.height * 0.27).toInt() / 16 * 16
        val traces = methods.map { IvpOperations.searchTrace(reference, current, bx, by, it) }

        // Blank paper blocks have no structure to match, so their vectors follow noise. Only textured blocks vote.
        val textured = exhaustive.vectors.map { blockStd(reference, it.blockX, it.blockY) >= TEXTURE_STD }
        val texturedEs = exhaustive.vectors.filterIndexed { i, _ -> textured[i] }
        val dominant = texturedEs.groupingBy { it.dx to it.dy }.eachCount().maxByOrNull { it.value }?.key
            ?: (exhaustive.dominantDx to exhaustive.dominantDy)
        val shake = sqrt((dominant.first * dominant.first + dominant.second * dominant.second).toDouble())
        fun agreement(s: MotionSearchSummary): String {
            val same = s.vectors.indices.count { i -> textured[i] && s.vectors[i].dx == exhaustive.vectors[i].dx && s.vectors[i].dy == exhaustive.vectors[i].dy }
            return pct(same, max(1, texturedEs.size))
        }
        val panels = listOf(
            LabPanel("Frame t-1 (reference, I-frame)", reference.toBitmap()),
            LabPanel("Frame t (current, P-frame)", current.toBitmap()),
            LabPanel("Frame difference |t - (t-1)| x 3", IvpOperations.absDifference(current, reference, 3).toBitmap()),
            LabPanel("Residual after motion compensation x 3", IvpOperations.absDifference(current, predicted, 3).toBitmap()),
            LabPanel("Exhaustive search vectors (16 x 16, +/-7)", vectorsBitmap(current, exhaustive, textured), wide = true),
            LabPanel("Three-step search vectors", vectorsBitmap(current, summaries[1], textured), wide = true),
            LabPanel("2-D logarithmic search vectors", vectorsBitmap(current, summaries[2], textured), wide = true),
            LabPanel("Points tested for one block: ES, TSS, 2-D log", searchPatternBitmap(traces), wide = true)
        )
        val metrics = buildList {
            summaries.forEach { s ->
                add(
                    s.method to "${fmt(s.meanPointsPerBlock, 1)} points/block (max ${s.maxPointsPerBlock}), " +
                        "mean SAD ${fmt(s.meanSad, 0)}, ${fmt(timings[s.method] ?: 0.0, 0)} ms"
                )
            }
            add("Blocks per frame" to "${exhaustive.vectors.size} (${reference.width} x ${reference.height} px), ${texturedEs.size} textured")
            add("Dominant vector (ES, textured)" to "(${dominant.first}, ${dominant.second}) px, |d| = ${fmt(shake, 1)} px")
            add("Textured blocks within 1 px of it" to pct(texturedEs.count { abs(it.dx - dominant.first) <= 1 && abs(it.dy - dominant.second) <= 1 }, max(1, texturedEs.size)))
            add("Same vector as ES" to "Three-step ${agreement(summaries[1])}, 2-D log ${agreement(summaries[2])} of textured blocks")
            add("Mean |residual| before / after" to "${fmt(residualBefore, 2)} / ${fmt(residualAfter, 2)} gray levels")
            add("Capture decision" to if (shake > 1.5) "Camera moving: wait for a steady frame" else "Steady: safe to capture")
            add("Speed-up vs exhaustive" to summaries.drop(1).joinToString { s ->
                "${s.method} ${fmt(exhaustive.meanPointsPerBlock / s.meanPointsPerBlock, 1)}x fewer points"
            })
        }
        return LabStageResult(
            panels, metrics,
            note = "Frame t is synthesised from frame t-1 with a 4 px / -3 px shift, a 0.4 degree turn and sensor noise, the size of movement a hand makes between preview frames. Arrows show where each textured block's content came from, drawn at twice their length; grey dots mark blank-paper blocks (16 x 16 standard deviation below 12), whose matches follow noise and are left out of the decision. A codec stores frame t as these vectors plus the small residual: that is a P-frame. The app can use the same vectors to wait for a steady phone before it captures."
        )
    }

    private const val TEXTURE_STD = 12.0

    private fun blockStd(image: GrayImage, bx: Int, by: Int, size: Int = 16): Double {
        var sum = 0.0
        var squares = 0.0
        for (y in by until by + size) for (x in bx until bx + size) {
            val v = image[x, y].toDouble()
            sum += v
            squares += v * v
        }
        val n = (size * size).toDouble()
        val mean = sum / n
        return sqrt(max(0.0, squares / n - mean * mean))
    }

    private fun vectorsBitmap(frame: GrayImage, summary: MotionSearchSummary, textured: List<Boolean>): Bitmap {
        val zoom = 2
        val bitmap = frame.toBitmap(zoom).copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val grid = Paint().apply { color = Color.argb(60, 31, 75, 216); strokeWidth = 1f }
        for (x in 0..frame.width step 16) canvas.drawLine(x * zoom.toFloat(), 0f, x * zoom.toFloat(), bitmap.height.toFloat(), grid)
        for (y in 0..frame.height step 16) canvas.drawLine(0f, y * zoom.toFloat(), bitmap.width.toFloat(), y * zoom.toFloat(), grid)
        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 40, 40); strokeWidth = 3f }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 27, 42) }
        val flat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 91, 100, 114) }
        summary.vectors.forEachIndexed { index, v ->
            val cx = (v.blockX + 8f) * zoom
            val cy = (v.blockY + 8f) * zoom
            if (!textured[index]) {
                canvas.drawCircle(cx, cy, 2f, flat)
                return@forEachIndexed
            }
            // Content moved from (block + d) to the block, so draw from the matched position to the block centre.
            val sx = cx + v.dx * zoom * 2f
            val sy = cy + v.dy * zoom * 2f
            canvas.drawLine(sx, sy, cx, cy, arrow)
            canvas.drawCircle(cx, cy, 3f, dot)
        }
        return bitmap
    }

    private fun searchPatternBitmap(traces: List<Pair<List<Pair<Int, Int>>, MotionVector>>): Bitmap {
        val cell = 22
        val size = 15 * cell
        val gap = 30
        val label = 46
        val bitmap = Bitmap.createBitmap(size * 3 + gap * 4, size + label + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val gridPaint = Paint().apply { color = Color.rgb(222, 214, 190); strokeWidth = 1f }
        val visited = Paint().apply { color = Color.rgb(31, 75, 216) }
        val best = Paint().apply { color = Color.rgb(214, 40, 40) }
        val centre = Paint().apply { color = Color.rgb(13, 27, 42); style = Paint.Style.STROKE; strokeWidth = 3f }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 27, 42); textSize = 26f }
        val names = listOf("ES", "TSS", "2-D log")
        traces.forEachIndexed { index, (points, vector) ->
            val left = gap + index * (size + gap)
            val top = label
            for (i in 0..15) {
                canvas.drawLine(left + i * cell.toFloat(), top.toFloat(), left + i * cell.toFloat(), (top + size).toFloat(), gridPaint)
                canvas.drawLine(left.toFloat(), top + i * cell.toFloat(), (left + size).toFloat(), top + i * cell.toFloat(), gridPaint)
            }
            points.forEach { (dx, dy) ->
                val x = left + (dx + 7) * cell
                val y = top + (dy + 7) * cell
                canvas.drawRect(x + 3f, y + 3f, x + cell - 3f, y + cell - 3f, visited)
            }
            val bx = left + (vector.dx + 7) * cell
            val by = top + (vector.dy + 7) * cell
            canvas.drawRect(bx + 1f, by + 1f, bx + cell - 1f, by + cell - 1f, best)
            canvas.drawRect(left + 7f * cell, top + 7f * cell, left + 8f * cell, top + 8f * cell, centre)
            canvas.drawText("${names[index]}: ${points.size} points", left.toFloat(), 32f, text)
        }
        return bitmap
    }

    // ---------------- helpers ----------------

    private data class Rect4(val x: Int, val y: Int, val w: Int, val h: Int)

    /** A text-rich band around the certificate title, used for zoomed comparisons. */
    private fun detailRect(image: GrayImage): Rect4 {
        val w = (image.width * 0.8).toInt().coerceAtLeast(16)
        val h = (image.height * 0.12).toInt().coerceAtLeast(16)
        val x = ((image.width - w) / 2).coerceAtLeast(0)
        val y = (image.height * 0.25).toInt().coerceAtMost(image.height - h)
        return Rect4(x, y, w, h)
    }

    private fun scaleTo(bitmap: Bitmap, maximum: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maximum) return bitmap
        val scale = maximum.toDouble() / largest
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1), true
        )
    }

    private fun toGray(bitmap: Bitmap): GrayImage {
        val rgba = Mat()
        val gray = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return gray.toGrayImageAndRelease()
    }

    private fun matToBitmap(mat: Mat): Bitmap =
        Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(mat, it) }

    private fun normalize8(source: Mat): GrayImage {
        val out = Mat()
        Core.normalize(source, out, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)
        return out.toGrayImageAndRelease()
    }

    private inline fun <T> withMat(image: GrayImage, block: (Mat) -> T): T {
        val mat = image.toMat()
        return try { block(mat) } finally { mat.release() }
    }

    private fun encodedSize(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): Long =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(format, quality, stream)
            stream.size().toLong()
        }

    private fun fmt(value: Double, digits: Int): String = String.format(Locale.US, "%.${digits}f", value)
    private fun db(value: Double): String = if (value.isInfinite()) "infinite (identical)" else "${fmt(value, 2)} dB"
    private fun pct(part: Int, total: Int): String = "${fmt(100.0 * part / total, 2)} %"
    private fun kib(bytes: Long): String = if (bytes >= 1024 * 1024) "${fmt(bytes / 1048576.0, 2)} MiB" else "${fmt(bytes / 1024.0, 1)} KiB"
    private fun ratio(raw: Long, encoded: Long): String = fmt(raw.toDouble() / max(1L, encoded), 1)
}

fun GrayImage.toMat(): Mat {
    val mat = Mat(height, width, CvType.CV_8UC1)
    mat.put(0, 0, ByteArray(pixels.size) { pixels[it].toByte() })
    return mat
}

fun Mat.toGrayImage(): GrayImage {
    require(type() == CvType.CV_8UC1) { "Expected an 8-bit single-channel Mat" }
    val continuous = if (isContinuous) this else clone()
    val bytes = ByteArray((continuous.total()).toInt())
    continuous.get(0, 0, bytes)
    if (continuous !== this) continuous.release()
    return GrayImage(cols(), rows(), IntArray(bytes.size) { bytes[it].toInt() and 0xFF })
}

fun Mat.toGrayImageAndRelease(): GrayImage = toGrayImage().also { release() }

/** Converts to an opaque bitmap, optionally enlarged by pixel replication so small crops stay crisp. */
fun GrayImage.toBitmap(zoom: Int = 1): Bitmap {
    val w = width * zoom
    val h = height * zoom
    val colors = IntArray(w * h) { index ->
        val v = pixels[(index / w / zoom) * width + (index % w) / zoom]
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
}
