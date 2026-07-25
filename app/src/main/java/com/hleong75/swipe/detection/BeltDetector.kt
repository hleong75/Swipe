package com.hleong75.swipe.detection

import android.graphics.Rect
import android.media.Image
import com.hleong75.swipe.Constants
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class DetectionType { CREAM_LOGO, GREEN_TIMER }

data class DetectionResult(
    val type: DetectionType,
    val x: Int,
    val y: Int,
    val score: Int,
    val columnIndex: Int,
    val beltRect: Rect
)

data class ScanStats(
    val creamColumn: Int,
    val creamCount: Int,
    val greenColumn: Int,
    val greenCount: Int
)

class BeltDetector {
    @Volatile
    var lastStats: ScanStats = ScanStats(-1, 0, -1, 0)
        private set

    private var cachedBeltRect: Rect? = null
    private var cachedWidth: Int = -1
    private var cachedHeight: Int = -1
    private var creamCounts = IntArray(0)
    private var greenCounts = IntArray(0)

    fun detect(image: Image): DetectionResult? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val belt = beltRect(width, height)
        if (belt.width() <= 0 || belt.height() <= 0) return null

        val columns = ceil(belt.width() / Constants.COLUMN_WIDTH.toDouble()).toInt().coerceAtLeast(1)
        if (creamCounts.size != columns) {
            creamCounts = IntArray(columns)
            greenCounts = IntArray(columns)
        } else {
            creamCounts.fill(0)
            greenCounts.fill(0)
        }

        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val sampleStep = Constants.SAMPLE_STEP.coerceAtLeast(1)

        var y = belt.top
        while (y < belt.bottom) {
            val rowStart = y * rowStride
            var x = belt.left
            while (x < belt.right) {
                val offset = rowStart + x * pixelStride
                if (offset + 2 < buffer.limit()) {
                    val r = buffer.get(offset).toInt() and 0xFF
                    val g = buffer.get(offset + 1).toInt() and 0xFF
                    val b = buffer.get(offset + 2).toInt() and 0xFF
                    val col = ((x - belt.left) / Constants.COLUMN_WIDTH).coerceIn(0, columns - 1)

                    if (isCream(r, g, b)) creamCounts[col]++
                    if (isGreen(r, g, b)) greenCounts[col]++
                }
                x += sampleStep
            }
            y += sampleStep
        }

        val (bestCreamCol, bestCreamCount) = bestColumn(creamCounts)
        val (bestGreenCol, bestGreenCount) = bestColumn(greenCounts)
        lastStats = ScanStats(bestCreamCol, bestCreamCount, bestGreenCol, bestGreenCount)

        val creamOk = bestCreamCount >= Constants.CREAM_MIN_MATCH
        val greenOk = bestGreenCount >= Constants.GREEN_MIN_MATCH
        if (!creamOk && !greenOk) return null

        val useCream = creamOk && (!greenOk || bestCreamCount >= bestGreenCount)
        val chosenCol = if (useCream) bestCreamCol else bestGreenCol
        val chosenScore = if (useCream) bestCreamCount else bestGreenCount
        val chosenType = if (useCream) DetectionType.CREAM_LOGO else DetectionType.GREEN_TIMER

        val columnLeft = belt.left + chosenCol * Constants.COLUMN_WIDTH
        val xCenter = min(width - 1, columnLeft + (Constants.COLUMN_WIDTH / 2))
        val yCenter = belt.centerY()

        return DetectionResult(
            type = chosenType,
            x = xCenter,
            y = yCenter,
            score = chosenScore,
            columnIndex = chosenCol,
            beltRect = Rect(belt)
        )
    }

    private fun beltRect(screenWidth: Int, screenHeight: Int): Rect {
        if (cachedBeltRect != null && cachedWidth == screenWidth && cachedHeight == screenHeight) {
            return cachedBeltRect!!
        }

        val x = (Constants.BELT_X_RATIO * screenWidth).toInt().coerceIn(0, max(screenWidth - 1, 0))
        val y = (Constants.BELT_Y_RATIO * screenHeight).toInt().coerceIn(0, max(screenHeight - 1, 0))
        val w = (Constants.BELT_W_RATIO * screenWidth).toInt().coerceAtLeast(1)
        val h = (Constants.BELT_H_RATIO * screenHeight).toInt().coerceAtLeast(1)

        val right = min(screenWidth, x + w)
        val bottom = min(screenHeight, y + h)
        cachedBeltRect = Rect(x, y, right, bottom)
        cachedWidth = screenWidth
        cachedHeight = screenHeight
        return cachedBeltRect!!
    }

    private fun bestColumn(values: IntArray): Pair<Int, Int> {
        var bestCol = 0
        var bestCount = 0
        for (i in values.indices) {
            if (values[i] > bestCount) {
                bestCount = values[i]
                bestCol = i
            }
        }
        return bestCol to bestCount
    }

    private fun isCream(r: Int, g: Int, b: Int): Boolean {
        return r >= Constants.CREAM_MIN_R &&
            g >= Constants.CREAM_MIN_G &&
            b >= Constants.CREAM_MIN_B &&
            b <= Constants.CREAM_MAX_B &&
            (r - b) >= Constants.MIN_RB_GAP
    }

    private fun isGreen(r: Int, g: Int, b: Int): Boolean {
        return (g - r) >= Constants.GREEN_MARGIN_RB &&
            (g - b) >= Constants.GREEN_MARGIN_GB &&
            g >= Constants.GREEN_MIN_G
    }
}
