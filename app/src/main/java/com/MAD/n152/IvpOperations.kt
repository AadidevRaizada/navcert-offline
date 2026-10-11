package com.MAD.n152

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * An 8-bit grayscale image stored row-major. Values are 0..255.
 * Kept free of Android and OpenCV types so every algorithm here runs in plain JVM unit tests.
 */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray = IntArray(width * height)) {
    init {
        require(width > 0 && height > 0) { "Image must not be empty" }
        require(pixels.size == width * height) { "Pixel count does not match size" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
    operator fun set(x: Int, y: Int, value: Int) {
        pixels[y * width + x] = value
    }

    fun map(transform: (Int) -> Int): GrayImage = GrayImage(width, height, IntArray(pixels.size) { transform(pixels[it]) })
    fun applyLut(lut: IntArray): GrayImage = GrayImage(width, height, IntArray(pixels.size) { lut[pixels[it]] })
    fun copy(): GrayImage = GrayImage(width, height, pixels.copyOf())
}

data class MotionVector(
    val blockX: Int,
    val blockY: Int,
    val dx: Int,
    val dy: Int,
    val sad: Long,
    val pointsEvaluated: Int
)

data class MotionSearchSummary(
    val method: String,
    val vectors: List<MotionVector>,
    val meanPointsPerBlock: Double,
    val maxPointsPerBlock: Int,
    val meanSad: Double,
    val dominantDx: Int,
    val dominantDy: Int
)

data class DctCompressionResult(
    val reconstructed: GrayImage,
    val coefficientsKept: Int,
    val coefficientsTotal: Int,
    val psnr: Double
) {
    val keptPercent: Double get() = 100.0 * coefficientsKept / coefficientsTotal
}

data class ComponentLabels(val labels: IntArray, val count: Int)

/** Pure implementations of the Image & Video Processing algorithms shown in the Pipeline Lab. */
object IvpOperations {

    // ---------- Unit 1: fundamentals ----------

    /** Reduces gray-level resolution to [levels] evenly spaced levels across 0..255. */
    fun requantize(image: GrayImage, levels: Int): GrayImage {
        require(levels in 2..256)
        val step = 256.0 / levels
        return image.map { value ->
            val bin = min(levels - 1, (value / step).toInt())
            (bin * 255.0 / (levels - 1)).roundToInt()
        }
    }

    /** Spatial-resolution demo: keep every [factor]-th pixel, then enlarge with pixel replication. */
    fun downsampleReplicate(image: GrayImage, factor: Int): GrayImage {
        require(factor >= 1)
        return GrayImage(image.width, image.height, IntArray(image.pixels.size) { index ->
            val x = (index % image.width) / factor * factor
            val y = (index / image.width) / factor * factor
            image[x, y]
        })
    }

    fun euclideanDistance(x1: Int, y1: Int, x2: Int, y2: Int): Double =
        sqrt(((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)).toDouble())

    fun cityBlockDistance(x1: Int, y1: Int, x2: Int, y2: Int): Int = abs(x1 - x2) + abs(y1 - y2)

    fun chessboardDistance(x1: Int, y1: Int, x2: Int, y2: Int): Int = max(abs(x1 - x2), abs(y1 - y2))

    // ---------- Unit 2: point processing ----------

    fun negativeLut(): IntArray = IntArray(256) { 255 - it }

    /** Piecewise-linear contrast stretch through (r1, s1) and (r2, s2). */
    fun contrastStretchLut(r1: Int, s1: Int, r2: Int, s2: Int): IntArray {
        require(r1 < r2)
        return IntArray(256) { r ->
            val s = when {
                r <= r1 -> if (r1 == 0) s1.toDouble() else s1.toDouble() * r / r1
                r >= r2 -> if (r2 == 255) s2.toDouble() else s2 + (255.0 - s2) * (r - r2) / (255.0 - r2)
                else -> s1 + (s2 - s1).toDouble() * (r - r1) / (r2 - r1)
            }
            s.roundToInt().coerceIn(0, 255)
        }
    }

    /** s = c log(1 + r) with c chosen so that 255 maps to 255. */
    fun logLut(): IntArray {
        val c = 255.0 / ln(256.0)
        return IntArray(256) { (c * ln(1.0 + it)).roundToInt().coerceIn(0, 255) }
    }

    /** Power-law (gamma) transform s = 255 (r / 255)^gamma. */
    fun gammaLut(gamma: Double): IntArray {
        require(gamma > 0)
        return IntArray(256) { (255.0 * (it / 255.0).pow(gamma)).roundToInt().coerceIn(0, 255) }
    }

    /** Intensity-level slicing: highlight [low, high]; other levels are kept (preserve) or set to [background]. */
    fun intensitySliceLut(low: Int, high: Int, highlight: Int = 255, preserve: Boolean = true, background: Int = 0): IntArray =
        IntArray(256) { r -> if (r in low..high) highlight else if (preserve) r else background }

    fun threshold(image: GrayImage, t: Int): GrayImage = image.map { if (it > t) 255 else 0 }

    // ---------- Unit 2: histogram processing ----------

    fun histogram(image: GrayImage): IntArray {
        val hist = IntArray(256)
        image.pixels.forEach { hist[it]++ }
        return hist
    }

    /** Global histogram equalization: s_k = round((cdf(r_k) - cdf_min) / (N - cdf_min) * 255). */
    fun equalize(image: GrayImage): GrayImage {
        val hist = histogram(image)
        val total = image.pixels.size
        val cdf = IntArray(256)
        var running = 0
        for (i in 0..255) {
            running += hist[i]
            cdf[i] = running
        }
        val cdfMin = cdf.first { it > 0 }
        if (cdfMin == total) return image.copy()
        val lut = IntArray(256) { level ->
            (((cdf[level] - cdfMin).toDouble() / (total - cdfMin)) * 255.0).roundToInt().coerceIn(0, 255)
        }
        return image.applyLut(lut)
    }

    fun mean(image: GrayImage): Double = image.pixels.average()

    fun standardDeviation(image: GrayImage): Double {
        val m = mean(image)
        return sqrt(image.pixels.sumOf { (it - m) * (it - m) } / image.pixels.size)
    }

    /** Shannon entropy of the gray-level histogram in bits per pixel. */
    fun entropy(hist: IntArray): Double {
        val total = hist.sum().toDouble()
        return hist.filter { it > 0 }.sumOf { val p = it / total; -p * ln(p) / ln(2.0) }
    }

    /** Gray level below which [fraction] of the pixels fall. */
    fun percentile(hist: IntArray, fraction: Double): Int {
        val target = hist.sum() * fraction
        var running = 0.0
        for (i in 0..255) {
            running += hist[i]
            if (running >= target) return i
        }
        return 255
    }

    // ---------- Unit 6: segmentation ----------

    /**
     * Otsu's method. Returns T maximising the between-class variance, with class 0 = [0, T] and
     * class 1 = [T + 1, 255]. Ties choose the middle of the plateau so a gap between two modes splits evenly.
     */
    fun otsuThreshold(hist: IntArray): Int {
        require(hist.size == 256)
        val total = hist.sum().toDouble()
        if (total == 0.0) return 0
        val sumAll = (0..255).sumOf { it.toDouble() * hist[it] }
        var weightBackground = 0.0
        var sumBackground = 0.0
        var bestVariance = -1.0
        var firstBest = 0
        var lastBest = 0
        for (t in 0..254) {
            weightBackground += hist[t]
            if (weightBackground == 0.0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0.0) break
            sumBackground += t.toDouble() * hist[t]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sumAll - sumBackground) / weightForeground
            val variance = weightBackground * weightForeground * (meanBackground - meanForeground).pow(2)
            if (variance > bestVariance + 1e-9) {
                bestVariance = variance
                firstBest = t
                lastBest = t
            } else if (abs(variance - bestVariance) <= 1e-9) {
                lastBest = t
            }
        }
        return (firstBest + lastBest) / 2
    }

    /**
     * Connected-component labelling of non-zero pixels with 4- or 8-connectivity.
     * Labels start at 1; background stays 0.
     */
    fun connectedComponents(binary: GrayImage, connectivity: Int): ComponentLabels {
        require(connectivity == 4 || connectivity == 8)
        val w = binary.width
        val h = binary.height
        val labels = IntArray(w * h)
        val stack = IntArray(w * h)
        val offsets = if (connectivity == 4) {
            arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        } else {
            arrayOf(
                intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
                intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1)
            )
        }
        var next = 0
        for (start in 0 until w * h) {
            if (binary.pixels[start] == 0 || labels[start] != 0) continue
            next++
            var top = 0
            stack[top++] = start
            labels[start] = next
            while (top > 0) {
                val index = stack[--top]
                val x = index % w
                val y = index / w
                for (offset in offsets) {
                    val nx = x + offset[0]
                    val ny = y + offset[1]
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val neighbour = ny * w + nx
                    if (binary.pixels[neighbour] != 0 && labels[neighbour] == 0) {
                        labels[neighbour] = next
                        stack[top++] = neighbour
                    }
                }
            }
        }
        return ComponentLabels(labels, next)
    }

    // ---------- Unit 4: binary morphology (square structuring element) ----------

    fun erode(binary: GrayImage, size: Int = 3): GrayImage = morph(binary, size, erode = true)

    fun dilate(binary: GrayImage, size: Int = 3): GrayImage = morph(binary, size, erode = false)

    /** Boundary extraction: beta(A) = A - (A eroded by B). */
    fun boundary(binary: GrayImage, size: Int = 3): GrayImage {
        val eroded = erode(binary, size)
        return GrayImage(binary.width, binary.height, IntArray(binary.pixels.size) {
            if (binary.pixels[it] != 0 && eroded.pixels[it] == 0) 255 else 0
        })
    }

    private fun morph(binary: GrayImage, size: Int, erode: Boolean): GrayImage {
        val r = size / 2
        val out = GrayImage(binary.width, binary.height)
        for (y in 0 until binary.height) for (x in 0 until binary.width) {
            var result = erode
            loop@ for (j in -r..r) for (i in -r..r) {
                val nx = x + i
                val ny = y + j
                // Pixels outside the image count as background.
                val on = nx in 0 until binary.width && ny in 0 until binary.height && binary[nx, ny] != 0
                if (erode && !on) { result = false; break@loop }
                if (!erode && on) { result = true; break@loop }
            }
            out[x, y] = if (result) 255 else 0
        }
        return out
    }

    // ---------- Unit 3: DCT and compression ----------

    /** Standard JPEG luminance quantisation table (ITU-T T.81 Annex K, Table K.1), row-major. */
    val JPEG_LUMINANCE = intArrayOf(
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99
    )

    private val cosTable = Array(8) { u -> DoubleArray(8) { x -> cos((2 * x + 1) * u * PI / 16.0) } }
    private val alphaTable = DoubleArray(8) { if (it == 0) sqrt(1.0 / 8.0) else sqrt(2.0 / 8.0) }
    private fun alpha(u: Int) = alphaTable[u]

    /** Orthonormal 2-D DCT-II of one 8x8 block (row-major, 64 values), computed separably: rows, then columns. */
    fun dct8x8(block: DoubleArray): DoubleArray {
        require(block.size == 64)
        val rows = DoubleArray(64)
        for (y in 0..7) for (u in 0..7) {
            var sum = 0.0
            for (x in 0..7) sum += block[y * 8 + x] * cosTable[u][x]
            rows[y * 8 + u] = alpha(u) * sum
        }
        val out = DoubleArray(64)
        for (u in 0..7) for (v in 0..7) {
            var sum = 0.0
            for (y in 0..7) sum += rows[y * 8 + u] * cosTable[v][y]
            out[v * 8 + u] = alpha(v) * sum
        }
        return out
    }

    /** Inverse of [dct8x8]. */
    fun idct8x8(coefficients: DoubleArray): DoubleArray {
        require(coefficients.size == 64)
        val columns = DoubleArray(64)
        for (u in 0..7) for (y in 0..7) {
            var sum = 0.0
            for (v in 0..7) sum += alpha(v) * coefficients[v * 8 + u] * cosTable[v][y]
            columns[y * 8 + u] = sum
        }
        val out = DoubleArray(64)
        for (y in 0..7) for (x in 0..7) {
            var sum = 0.0
            for (u in 0..7) sum += alpha(u) * columns[y * 8 + u] * cosTable[u][x]
            out[y * 8 + x] = sum
        }
        return out
    }

    /** IJG scaling of the luminance table for a quality factor 1..100. */
    fun scaledQuantTable(quality: Int): IntArray {
        val q = quality.coerceIn(1, 100)
        val scale = if (q < 50) 5000 / q else 200 - 2 * q
        return IntArray(64) { ((JPEG_LUMINANCE[it] * scale + 50) / 100).coerceIn(1, 255) }
    }

    /** JPEG-style 8x8 block DCT with quantisation at [quality]. Counts non-zero quantised coefficients. */
    fun dctQuantize(image: GrayImage, quality: Int): DctCompressionResult {
        val table = scaledQuantTable(quality)
        return blockTransform(image) { coefficients ->
            var kept = 0
            for (i in 0..63) {
                val level = (coefficients[i] / table[i]).roundToInt()
                if (level != 0) kept++
                coefficients[i] = (level * table[i]).toDouble()
            }
            kept
        }
    }

    /** Keeps only the [k] largest-magnitude DCT coefficients in every 8x8 block. */
    fun dctTopK(image: GrayImage, k: Int): DctCompressionResult {
        require(k in 1..64)
        return blockTransform(image) { coefficients ->
            val order = (0..63).sortedByDescending { abs(coefficients[it]) }
            order.drop(k).forEach { coefficients[it] = 0.0 }
            k
        }
    }

    /** Log-magnitude of all block DCT coefficients, scaled to 0..255 for display. */
    fun dctMagnitudeImage(image: GrayImage): GrayImage {
        val w = image.width / 8 * 8
        val h = image.height / 8 * 8
        val logs = DoubleArray(w * h)
        val block = DoubleArray(64)
        for (by in 0 until h step 8) for (bx in 0 until w step 8) {
            for (y in 0..7) for (x in 0..7) block[y * 8 + x] = image[bx + x, by + y] - 128.0
            val c = dct8x8(block)
            for (y in 0..7) for (x in 0..7) logs[(by + y) * w + bx + x] = ln(1.0 + abs(c[y * 8 + x]))
        }
        val maxLog = logs.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        return GrayImage(w, h, IntArray(w * h) { (logs[it] / maxLog * 255.0).roundToInt() })
    }

    private inline fun blockTransform(image: GrayImage, process: (DoubleArray) -> Int): DctCompressionResult {
        val w = image.width / 8 * 8
        val h = image.height / 8 * 8
        require(w >= 8 && h >= 8) { "Image must be at least 8x8" }
        val source = crop(image, 0, 0, w, h)
        val out = GrayImage(w, h)
        val block = DoubleArray(64)
        var kept = 0
        for (by in 0 until h step 8) for (bx in 0 until w step 8) {
            for (y in 0..7) for (x in 0..7) block[y * 8 + x] = source[bx + x, by + y] - 128.0
            val coefficients = dct8x8(block)
            kept += process(coefficients)
            val restored = idct8x8(coefficients)
            for (y in 0..7) for (x in 0..7) {
                out[bx + x, by + y] = (restored[y * 8 + x] + 128.0).roundToInt().coerceIn(0, 255)
            }
        }
        return DctCompressionResult(out, kept, w * h, psnr(source, out))
    }

    fun mse(a: GrayImage, b: GrayImage): Double {
        require(a.width == b.width && a.height == b.height)
        var sum = 0.0
        for (i in a.pixels.indices) {
            val d = (a.pixels[i] - b.pixels[i]).toDouble()
            sum += d * d
        }
        return sum / a.pixels.size
    }

    /** Peak signal-to-noise ratio in dB for 8-bit images; identical images give +infinity. */
    fun psnr(a: GrayImage, b: GrayImage): Double {
        val error = mse(a, b)
        return if (error == 0.0) Double.POSITIVE_INFINITY else 10.0 * log10(255.0 * 255.0 / error)
    }

    fun crop(image: GrayImage, x0: Int, y0: Int, w: Int, h: Int): GrayImage {
        require(x0 >= 0 && y0 >= 0 && x0 + w <= image.width && y0 + h <= image.height)
        return GrayImage(w, h, IntArray(w * h) { image[x0 + it % w, y0 + it / w] })
    }

    /** Absolute difference |a - b| scaled by [gain] (for error maps). */
    fun absDifference(a: GrayImage, b: GrayImage, gain: Int = 1): GrayImage {
        require(a.width == b.width && a.height == b.height)
        return GrayImage(a.width, a.height, IntArray(a.pixels.size) {
            (abs(a.pixels[it] - b.pixels[it]) * gain).coerceAtMost(255)
        })
    }

    // ---------- Unit 8: motion estimation ----------

    /** Translates an image by (dx, dy) with edge replication: out(x, y) = in(x - dx, y - dy). */
    fun shift(image: GrayImage, dx: Int, dy: Int): GrayImage = GrayImage(image.width, image.height, IntArray(image.pixels.size) {
        val x = (it % image.width - dx).coerceIn(0, image.width - 1)
        val y = (it / image.width - dy).coerceIn(0, image.height - 1)
        image[x, y]
    })

    /** Sum of absolute differences between the current block at (bx, by) and the reference block displaced by (dx, dy). */
    fun sad(reference: GrayImage, current: GrayImage, bx: Int, by: Int, dx: Int, dy: Int, blockSize: Int): Long {
        var sum = 0L
        for (y in 0 until blockSize) {
            val currentRow = (by + y) * current.width
            val referenceRow = (by + dy + y) * reference.width
            for (x in 0 until blockSize) {
                sum += abs(current.pixels[currentRow + bx + x] - reference.pixels[referenceRow + bx + dx + x])
            }
        }
        return sum
    }

    /** The displacements a method visits for one block, in visiting order (for drawing the search pattern). */
    fun searchTrace(
        reference: GrayImage,
        current: GrayImage,
        bx: Int,
        by: Int,
        method: String,
        blockSize: Int = 16,
        range: Int = 7
    ): Pair<List<Pair<Int, Int>>, MotionVector> {
        val search = BlockSearch(reference, current, bx, by, blockSize, range)
        when (method) {
            METHOD_EXHAUSTIVE -> runExhaustive(search)
            METHOD_THREE_STEP -> runThreeStep(search)
            METHOD_TWO_D_LOG -> runTwoDLog(search)
            else -> error("Unknown method $method")
        }
        return search.order.toList() to search.result()
    }

    /** Evaluates candidates once each and remembers the best; counts unique search points. */
    private class BlockSearch(
        val reference: GrayImage,
        val current: GrayImage,
        val bx: Int,
        val by: Int,
        val blockSize: Int,
        val range: Int
    ) {
        private val evaluated = HashMap<Int, Long>()
        val order = ArrayList<Pair<Int, Int>>()
        var bestDx = 0
        var bestDy = 0
        var bestSad = Long.MAX_VALUE
        val points: Int get() = evaluated.size

        fun valid(dx: Int, dy: Int): Boolean =
            abs(dx) <= range && abs(dy) <= range &&
                bx + dx >= 0 && by + dy >= 0 &&
                bx + dx + blockSize <= reference.width && by + dy + blockSize <= reference.height

        /** Returns the SAD at (dx, dy) or null if it falls outside the window or frame. */
        fun evaluate(dx: Int, dy: Int): Long? {
            if (!valid(dx, dy)) return null
            val key = (dy + 64) * 256 + (dx + 64)
            val cost = evaluated.getOrPut(key) {
                order += dx to dy
                sad(reference, current, bx, by, dx, dy, blockSize)
            }
            // Prefer the smaller displacement on ties so static blocks report (0, 0).
            if (cost < bestSad || (cost == bestSad && dx * dx + dy * dy < bestDx * bestDx + bestDy * bestDy)) {
                bestSad = cost
                bestDx = dx
                bestDy = dy
            }
            return cost
        }

        fun result() = MotionVector(bx, by, bestDx, bestDy, bestSad, points)
    }

    /** Full search: every displacement in [-range, range]^2, i.e. (2p + 1)^2 points per block. */
    fun exhaustiveSearch(reference: GrayImage, current: GrayImage, bx: Int, by: Int, blockSize: Int = 16, range: Int = 7): MotionVector =
        BlockSearch(reference, current, bx, by, blockSize, range).also(::runExhaustive).result()

    private fun runExhaustive(search: BlockSearch) {
        val range = search.range
        for (dy in -range..range) for (dx in -range..range) search.evaluate(dx, dy)
    }

    /**
     * Three-step search (Koga et al.): start with step ceil(p / 2) (4 for p = 7), test the centre and its 8 neighbours,
     * move to the best and halve the step until it reaches 1. At most 9 + 8 + 8 = 25 points for p = 7.
     */
    fun threeStepSearch(reference: GrayImage, current: GrayImage, bx: Int, by: Int, blockSize: Int = 16, range: Int = 7): MotionVector =
        BlockSearch(reference, current, bx, by, blockSize, range).also(::runThreeStep).result()

    private fun runThreeStep(search: BlockSearch) {
        var cx = 0
        var cy = 0
        var step = max(1, (search.range + 1) / 2)
        search.evaluate(0, 0)
        while (step >= 1) {
            for (j in -1..1) for (i in -1..1) search.evaluate(cx + i * step, cy + j * step)
            cx = search.bestDx
            cy = search.bestDy
            step /= 2
        }
    }

    /**
     * 2-D logarithmic search (Jain and Jain): test the centre and the four points of a '+' at the current step.
     * If the best point is the centre (or on the window edge) halve the step, otherwise move there.
     * When the step reaches 1, test all 8 neighbours of the centre and stop. Initial step 2^(ceil(log2 p) - 1).
     */
    fun twoDLogSearch(reference: GrayImage, current: GrayImage, bx: Int, by: Int, blockSize: Int = 16, range: Int = 7): MotionVector =
        BlockSearch(reference, current, bx, by, blockSize, range).also(::runTwoDLog).result()

    private fun runTwoDLog(search: BlockSearch) {
        val range = search.range
        var k = 0
        while ((1 shl k) < range) k++
        var step = max(1, 1 shl max(0, k - 1))
        var cx = 0
        var cy = 0
        search.evaluate(0, 0)
        var guard = 0
        while (guard++ < 64) {
            if (step == 1) {
                for (j in -1..1) for (i in -1..1) search.evaluate(cx + i, cy + j)
                break
            }
            search.evaluate(cx + step, cy)
            search.evaluate(cx - step, cy)
            search.evaluate(cx, cy + step)
            search.evaluate(cx, cy - step)
            val nx = search.bestDx
            val ny = search.bestDy
            if ((nx == cx && ny == cy) || abs(nx) == range || abs(ny) == range) {
                cx = nx
                cy = ny
                step /= 2
            } else {
                cx = nx
                cy = ny
            }
        }
    }

    /** Runs one search method over every complete block of the current frame and summarises it. */
    fun estimateMotion(
        reference: GrayImage,
        current: GrayImage,
        method: String,
        blockSize: Int = 16,
        range: Int = 7
    ): MotionSearchSummary {
        require(reference.width == current.width && reference.height == current.height)
        val vectors = ArrayList<MotionVector>()
        for (by in 0..current.height - blockSize step blockSize) {
            for (bx in 0..current.width - blockSize step blockSize) {
                vectors += when (method) {
                    METHOD_EXHAUSTIVE -> exhaustiveSearch(reference, current, bx, by, blockSize, range)
                    METHOD_THREE_STEP -> threeStepSearch(reference, current, bx, by, blockSize, range)
                    METHOD_TWO_D_LOG -> twoDLogSearch(reference, current, bx, by, blockSize, range)
                    else -> error("Unknown method $method")
                }
            }
        }
        val dominant = vectors.groupingBy { it.dx to it.dy }.eachCount().maxByOrNull { it.value }?.key ?: (0 to 0)
        return MotionSearchSummary(
            method = method,
            vectors = vectors,
            meanPointsPerBlock = vectors.map { it.pointsEvaluated }.average(),
            maxPointsPerBlock = vectors.maxOf { it.pointsEvaluated },
            meanSad = vectors.map { it.sad.toDouble() }.average(),
            dominantDx = dominant.first,
            dominantDy = dominant.second
        )
    }

    /** Motion-compensated prediction of the current frame from the reference (the P-frame predictor). */
    fun motionCompensate(reference: GrayImage, vectors: List<MotionVector>, blockSize: Int = 16): GrayImage {
        val out = reference.copy()
        vectors.forEach { v ->
            for (y in 0 until blockSize) for (x in 0 until blockSize) {
                out[v.blockX + x, v.blockY + y] = reference[v.blockX + v.dx + x, v.blockY + v.dy + y]
            }
        }
        return out
    }

    fun meanAbsoluteDifference(a: GrayImage, b: GrayImage): Double {
        require(a.width == b.width && a.height == b.height)
        return a.pixels.indices.sumOf { abs(a.pixels[it] - b.pixels[it]).toLong() }.toDouble() / a.pixels.size
    }

    const val METHOD_EXHAUSTIVE = "Exhaustive"
    const val METHOD_THREE_STEP = "Three-step"
    const val METHOD_TWO_D_LOG = "2-D log"
}
