package com.MAD.n152

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class IvpOperationsTest {

    /** Smooth, non-repeating texture so block matching has a unique minimum. */
    private fun texture(width: Int, height: Int, seed: Int = 7): GrayImage {
        val random = Random(seed)
        val noise = IntArray(width * height) { random.nextInt(0, 40) }
        return GrayImage(width, height, IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            (110 + 60 * sin(x * 0.21 + y * 0.05) + 40 * sin(y * 0.17 - x * 0.03) + noise[i] - 20).toInt().coerceIn(0, 255)
        })
    }

    @Test
    fun histogramCountsEveryPixel() {
        val image = GrayImage(3, 2, intArrayOf(0, 0, 10, 255, 10, 10))
        val hist = IvpOperations.histogram(image)
        assertEquals(2, hist[0])
        assertEquals(3, hist[10])
        assertEquals(1, hist[255])
        assertEquals(6, hist.sum())
    }

    @Test
    fun otsuSplitsBimodalHistogramBetweenModes() {
        val hist = IntArray(256)
        hist[40] = 500
        hist[200] = 500
        val t = IvpOperations.otsuThreshold(hist)
        assertTrue("T=$t must separate the modes", t in 40 until 200)
        assertEquals(119, t)
    }

    @Test
    fun otsuOnNoisyBimodalImageSeparatesClasses() {
        val random = Random(3)
        val pixels = IntArray(4000) { if (it < 2400) 60 + random.nextInt(-15, 16) else 190 + random.nextInt(-15, 16) }
        val t = IvpOperations.otsuThreshold(IvpOperations.histogram(GrayImage(80, 50, pixels)))
        assertTrue("T=$t", t in 76..174)
    }

    @Test
    fun equalizationSpreadsLevelsToFullRange() {
        val image = GrayImage(4, 1, intArrayOf(100, 101, 102, 103))
        val equalized = IvpOperations.equalize(image)
        assertArrayEquals(intArrayOf(0, 85, 170, 255), equalized.pixels)
    }

    @Test
    fun pointTransformsHaveExpectedEndpoints() {
        assertEquals(255, IvpOperations.negativeLut()[0])
        assertEquals(0, IvpOperations.negativeLut()[255])
        assertEquals(255, IvpOperations.logLut()[255])
        assertEquals(0, IvpOperations.logLut()[0])
        val gamma = IvpOperations.gammaLut(2.0)
        assertEquals(64, gamma[128])
        val stretch = IvpOperations.contrastStretchLut(50, 0, 200, 255)
        assertEquals(0, stretch[50])
        assertEquals(255, stretch[200])
        assertEquals(128, stretch[125])
        val slice = IvpOperations.intensitySliceLut(10, 20, preserve = false)
        assertEquals(255, slice[15])
        assertEquals(0, slice[30])
    }

    @Test
    fun requantizeProducesRequestedLevelCount() {
        val ramp = GrayImage(256, 1, IntArray(256) { it })
        val levels = IvpOperations.requantize(ramp, 8).pixels.distinct()
        assertEquals(8, levels.size)
        assertEquals(0, levels.min())
        assertEquals(255, levels.max())
    }

    @Test
    fun dctThenIdctRoundTripsABlock() {
        val random = Random(11)
        val block = DoubleArray(64) { random.nextInt(-128, 128).toDouble() }
        val restored = IvpOperations.idct8x8(IvpOperations.dct8x8(block))
        for (i in 0..63) assertEquals(block[i], restored[i], 1e-9)
    }

    @Test
    fun dctOfConstantBlockHasOnlyDcTerm() {
        val coefficients = IvpOperations.dct8x8(DoubleArray(64) { 10.0 })
        assertEquals(80.0, coefficients[0], 1e-9)
        for (i in 1..63) assertEquals(0.0, coefficients[i], 1e-9)
    }

    @Test
    fun keepingAllCoefficientsIsLossless() {
        val image = texture(32, 24)
        val result = IvpOperations.dctTopK(image, 64)
        assertEquals(Double.POSITIVE_INFINITY, result.psnr, 0.0)
        assertEquals(100.0, result.keptPercent, 1e-9)
    }

    @Test
    fun higherJpegQualityGivesHigherPsnrAndKeepsMoreCoefficients() {
        val image = texture(64, 64)
        val low = IvpOperations.dctQuantize(image, 10)
        val high = IvpOperations.dctQuantize(image, 90)
        assertTrue(high.psnr > low.psnr)
        assertTrue(high.coefficientsKept > low.coefficientsKept)
        assertEquals(IvpOperations.JPEG_LUMINANCE.toList(), IvpOperations.scaledQuantTable(50).toList())
    }

    @Test
    fun psnrOfIdenticalImagesIsInfiniteAndKnownForConstantError() {
        val a = GrayImage(4, 4, IntArray(16) { 100 })
        assertEquals(Double.POSITIVE_INFINITY, IvpOperations.psnr(a, a.copy()), 0.0)
        val b = GrayImage(4, 4, IntArray(16) { 110 })
        // MSE = 100 -> PSNR = 10 log10(65025 / 100) = 28.1308 dB
        assertEquals(28.1308, IvpOperations.psnr(a, b), 1e-3)
    }

    @Test
    fun connectedComponentsDifferBetweenFourAndEightConnectivity() {
        // Two diagonal pixels plus one isolated pixel.
        val pixels = intArrayOf(
            255, 0, 0, 0,
            0, 255, 0, 0,
            0, 0, 0, 255
        )
        val image = GrayImage(4, 3, pixels)
        assertEquals(3, IvpOperations.connectedComponents(image, 4).count)
        assertEquals(2, IvpOperations.connectedComponents(image, 8).count)
    }

    @Test
    fun boundaryOfSquareIsItsOutline() {
        val image = GrayImage(7, 7)
        for (y in 1..5) for (x in 1..5) image[x, y] = 255
        val boundary = IvpOperations.boundary(image)
        assertEquals(16, boundary.pixels.count { it != 0 })
        assertEquals(0, boundary[3, 3])
        assertEquals(255, boundary[1, 1])
        assertEquals(9, IvpOperations.erode(image).pixels.count { it != 0 })
        assertEquals(49, IvpOperations.dilate(image).pixels.count { it != 0 })
    }

    @Test
    fun exhaustiveSearchFindsTheExactShift() {
        val reference = texture(96, 96)
        val current = IvpOperations.shift(reference, 3, -2)
        val vector = IvpOperations.exhaustiveSearch(reference, current, 32, 32, 16, 7)
        assertEquals(-3, vector.dx)
        assertEquals(2, vector.dy)
        assertEquals(0L, vector.sad)
        assertEquals(225, vector.pointsEvaluated)
    }

    @Test
    fun threeStepSearchEvaluatesAtMostTwentyFivePoints() {
        val reference = texture(128, 128, seed = 5)
        val current = IvpOperations.shift(reference, 2, 1)
        val summary = IvpOperations.estimateMotion(reference, current, IvpOperations.METHOD_THREE_STEP)
        assertTrue(summary.maxPointsPerBlock <= 25)
        summary.vectors.forEach { assertTrue(it.pointsEvaluated <= 25) }
    }

    @Test
    fun fastSearchesUseFewerPointsThanExhaustive() {
        val reference = texture(128, 128, seed = 9)
        val current = IvpOperations.shift(reference, -4, 3)
        val es = IvpOperations.estimateMotion(reference, current, IvpOperations.METHOD_EXHAUSTIVE)
        val tss = IvpOperations.estimateMotion(reference, current, IvpOperations.METHOD_THREE_STEP)
        val log = IvpOperations.estimateMotion(reference, current, IvpOperations.METHOD_TWO_D_LOG)
        assertTrue(tss.meanPointsPerBlock < es.meanPointsPerBlock)
        assertTrue(log.meanPointsPerBlock < es.meanPointsPerBlock)
        assertEquals(4, es.dominantDx)
        assertEquals(-3, es.dominantDy)
        // Exhaustive search is optimal, so no other method can beat its mean SAD.
        assertTrue(es.meanSad <= tss.meanSad + 1e-9)
        assertTrue(es.meanSad <= log.meanSad + 1e-9)
    }

    @Test
    fun motionCompensationOfPureShiftRemovesInteriorResidual() {
        val reference = texture(96, 96, seed = 2)
        val current = IvpOperations.shift(reference, 2, 2)
        val es = IvpOperations.estimateMotion(reference, current, IvpOperations.METHOD_EXHAUSTIVE)
        val predicted = IvpOperations.motionCompensate(reference, es.vectors)
        val interior = es.vectors.filter { it.blockX >= 16 && it.blockY >= 16 }
        interior.forEach { assertEquals(0L, it.sad) }
        assertTrue(
            IvpOperations.meanAbsoluteDifference(current, predicted) <
                IvpOperations.meanAbsoluteDifference(current, reference)
        )
    }

    @Test
    fun distanceMeasures() {
        assertEquals(5.0, IvpOperations.euclideanDistance(0, 0, 3, 4), 1e-12)
        assertEquals(7, IvpOperations.cityBlockDistance(0, 0, 3, 4))
        assertEquals(4, IvpOperations.chessboardDistance(0, 0, 3, 4))
        assertTrue(abs(IvpOperations.entropy(intArrayOf(1, 1) + IntArray(254)) - 1.0) < 1e-12)
    }
}
