// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.contentfilter.ui.popover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.neeva.app.R
import com.neeva.app.contentfilter.TrackingData
import com.neeva.app.contentfilter.TrackingEntity
import com.neeva.app.ui.NeevaThemePreviewContainer
import com.neeva.app.ui.PortraitPreviews
import com.neeva.app.ui.theme.Dimensions

@Composable
fun TrackingDataDisplay(visible: Boolean, contentFilterPopoverModel: ContentFilterPopoverModel) {
    val trackingData by contentFilterPopoverModel.trackingDataFlow.collectAsState()
    val cookieNoticeBlocked by contentFilterPopoverModel.cookieNoticeBlocked.collectAsState()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically() + expandVertically(),
        exit = fadeOut() + slideOutVertically() + shrinkVertically()
    ) {
        TrackingDataDisplayContent(
            trackingData = trackingData,
            wasCookieNoticeBlocked = cookieNoticeBlocked
        )
    }
}

@Composable
fun TrackingDataNumberBox(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    TrackingDataSurface(modifier = modifier.fillMaxWidth()) {
        TrackingDataBox(label = label) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun TrackingDataDisplayContent(
    trackingData: TrackingData?,
    wasCookieNoticeBlocked: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            TrackingDataNumberBox(
                label = stringResource(id = R.string.content_filter_ads_and_trackers),
                value = trackingData?.numTrackers ?: 0,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            Spacer(Modifier.width(Dimensions.PADDING_SMALL))

            TrackingDataNumberBox(
                label = stringResource(id = R.string.content_filter_popups),
                value = if (wasCookieNoticeBlocked) 1 else 0,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Spacer(Modifier.size(Dimensions.PADDING_MEDIUM))

        AnimatedVisibility(
            visible = trackingData?.trackingEntities?.isNotEmpty() == true,
            enter = fadeIn() + expandIn(expandFrom = Alignment.TopCenter),
            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.TopCenter)
        ) {
            TrackingEntityBox(trackingEntities = trackingData?.trackingEntities)
        }

        Spacer(Modifier.size(Dimensions.PADDING_MEDIUM))
    }
}

@PortraitPreviews
@Composable
fun TrackingDataDisplayContentPreview() {
    NeevaThemePreviewContainer(useDarkTheme = false) {
        Surface {
            TrackingDataDisplayContent(
                trackingData = TrackingData(
                    numTrackers = 250,
                    trackingEntities = mapOf(
                        TrackingEntity.GOOGLE to 500,
                        TrackingEntity.FACEBOOK to 50
                    )
                ),
                wasCookieNoticeBlocked = true
            )
        }
    }
}
