package com.tuneitall.tuner.autoscroll

object AutoScrollOverlayPositioning {
    fun clampX(x: Int, screenWidth: Int, overlayWidth: Int, margin: Int): Int {
        val maxX = (screenWidth - overlayWidth - margin).coerceAtLeast(margin)
        return x.coerceIn(margin, maxX)
    }

    fun clampY(y: Int, screenHeight: Int, overlayHeight: Int, margin: Int): Int {
        val maxY = (screenHeight - overlayHeight - margin).coerceAtLeast(margin)
        return y.coerceIn(margin, maxY)
    }

    fun snapBubbleToEdge(x: Int, screenWidth: Int, bubbleWidth: Int): Int =
        if (x + bubbleWidth / 2 < screenWidth / 2) 0 else (screenWidth - bubbleWidth).coerceAtLeast(0)
}
