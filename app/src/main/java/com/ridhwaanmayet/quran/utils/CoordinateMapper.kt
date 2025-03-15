package com.ridhwaanmayet.quran.utils

import android.graphics.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

/**
 * Utility class to map HTML image map coordinates to actual screen coordinates. This is needed
 * because the coordinates in the HTML files are relative to the original image size, which may
 * differ from the displayed size on the device.
 */
class CoordinateMapper {
    companion object {
        // Original dimensions of the image maps (from HTML files)
        private const val ORIGINAL_WIDTH = 719.37f
        private const val ORIGINAL_HEIGHT = 1280.0f

        /** Maps coordinates from the original image size to the displayed size */
        fun mapRect(coords: List<Int>, displaySize: Size): Rect {
            val widthRatio = displaySize.width / ORIGINAL_WIDTH
            val heightRatio = displaySize.height / ORIGINAL_HEIGHT

            return Rect(
                    (coords[0] * widthRatio).toInt(),
                    (coords[1] * heightRatio).toInt(),
                    (coords[2] * widthRatio).toInt(),
                    (coords[3] * heightRatio).toInt()
            )
        }

        /** Checks if a touch point (x,y) is within a rectangle defined by the mapped coordinates */
        fun isPointInMappedRect(
                x: Float,
                y: Float,
                originalCoords: List<Int>,
                displaySize: Size
        ): Boolean {
            val rect = mapRect(originalCoords, displaySize)
            return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
        }

        /** Scales a single coordinate value */
        fun scaleX(x: Int, displayWidth: Float): Float {
            return x * (displayWidth / ORIGINAL_WIDTH)
        }

        fun scaleY(y: Int, displayHeight: Float): Float {
            return y * (displayHeight / ORIGINAL_HEIGHT)
        }

        /** Converts IntSize to Size for easier use with Compose functions */
        fun IntSize.toSize(): Size {
            return Size(width.toFloat(), height.toFloat())
        }
    }
}
