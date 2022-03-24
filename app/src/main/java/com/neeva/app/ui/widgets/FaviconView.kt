package com.neeva.app.ui.widgets

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neeva.app.NeevaConstants
import com.neeva.app.R
import com.neeva.app.storage.entities.Favicon.Companion.toBitmap
import com.neeva.app.ui.TwoBooleanPreviewContainer
import com.neeva.app.ui.theme.Dimensions

/**
 * Draws an icon representing a particular site.
 *
 * When determining what image to display, the [overrideDrawableId] is prioritized over the
 * [bitmap].  If both are null, then a generic icon is displayed.
 */
@Composable
fun FaviconView(
    bitmap: Bitmap?,
    drawContainer: Boolean = true,
    overrideDrawableId: Int? = null
) {
    Surface(
        color = if (drawContainer) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        },
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(Dimensions.RADIUS_SMALL)
    ) {
        val sizeModifier = Modifier.size(Dimensions.SIZE_ICON)
        Box(
            contentAlignment = Alignment.Center,
            modifier = if (drawContainer) {
                Modifier.padding(Dimensions.PADDING_SMALL)
            } else {
                Modifier
            }
        ) {
            when {
                overrideDrawableId != null -> {
                    // Tint is set to Color.unspecified because the current use case is to show a
                    // pre-colored drawable.
                    Icon(
                        painter = painterResource(overrideDrawableId),
                        contentDescription = null,
                        modifier = sizeModifier,
                        tint = Color.Unspecified
                    )
                }

                bitmap != null -> {
                    // Tint is set to Color.unspecified because the current use case is to show a
                    // pre-colored bitmap.
                    Icon(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = sizeModifier
                    )
                }

                else -> {
                    // Tint is set to the default so that it is tinted according to the current
                    // content color.
                    Icon(
                        painter = painterResource(R.drawable.globe),
                        contentDescription = null,
                        modifier = sizeModifier
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun FaviconViewPreviews() {
    TwoBooleanPreviewContainer { showBackground, showBitmap ->
        val bitmap = if (showBitmap) {
            Uri.parse(NeevaConstants.appURL).toBitmap()
        } else {
            null
        }

        FaviconView(
            bitmap = bitmap,
            drawContainer = showBackground
        )
    }
}