// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.settings.profile

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.neeva.app.R
import com.neeva.app.settings.sharedcomposables.SettingsUIConstants
import com.neeva.app.storage.toLetterBitmap

@Composable
fun ProfileImage(
    displayName: String?,
    painter: Painter?,
    circlePicture: Boolean,
    showSingleLetterPictureIfAvailable: Boolean
) {
    val regularModifier = Modifier.size(SettingsUIConstants.profilePictureSize)
    val circleClippedModifier = regularModifier.clip(CircleShape)
    when {
        painter != null -> {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = if (circlePicture) circleClippedModifier else regularModifier
            )
        }

        displayName != null && displayName.isNotEmpty() && showSingleLetterPictureIfAvailable -> {
            SingleLetterPicture(displayName, circleClippedModifier)
        }

        else -> {
            DefaultAccountImage(circleClippedModifier)
        }
    }
}

@Composable
private fun SingleLetterPicture(displayName: String, modifier: Modifier) {
    val bitmap = displayName.toLetterBitmap(0.50f, MaterialTheme.colorScheme.primary.toArgb())
        .asImageBitmap()
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun DefaultAccountImage(modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(id = R.drawable.ic_default_avatar),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(SettingsUIConstants.profilePictureSize / 2)
            )
        }
    }
}

@Composable
fun pictureUrlPainter(pictureURI: Uri?): Painter? {
    if (pictureURI == null || pictureURI.toString().isEmpty()) {
        return null
    }
    return rememberAsyncImagePainter(
        ImageRequest.Builder(LocalContext.current)
            .data(data = pictureURI)
            .apply(block = { crossfade(true) })
            .build()
    )
}
