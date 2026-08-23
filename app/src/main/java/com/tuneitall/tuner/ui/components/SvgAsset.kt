package com.tuneitall.tuner.ui.components

import androidx.annotation.RawRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.caverock.androidsvg.SVG
import kotlin.math.min

@Composable
internal fun SvgAsset(
    @RawRes resourceId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    stretchToBounds: Boolean = false,
) {
    val resources = LocalContext.current.resources
    val picture = remember(resources, resourceId) { SVG.getFromResource(resources, resourceId).renderToPicture() }
    val semanticsModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(semanticsModifier) {
        if (picture.width <= 0 || picture.height <= 0) return@Canvas
        val uniformScale = min(size.width / picture.width, size.height / picture.height)
        val scaleX = if (stretchToBounds) size.width / picture.width else uniformScale
        val scaleY = if (stretchToBounds) size.height / picture.height else uniformScale
        val left = (size.width - picture.width * scaleX) / 2f
        val top = (size.height - picture.height * scaleY) / 2f
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.translate(left, top)
            canvas.nativeCanvas.scale(scaleX, scaleY)
            canvas.nativeCanvas.drawPicture(picture)
            canvas.nativeCanvas.restore()
        }
    }
}
