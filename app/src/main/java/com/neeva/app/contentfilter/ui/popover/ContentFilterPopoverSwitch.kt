// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.contentfilter.ui.popover

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.neeva.app.R
import com.neeva.app.contentfilter.TrackersAllowList
import com.neeva.app.ui.NeevaSwitch

/**
 * [NeevaSwitch] that disables when tapped and enables when the [trackersAllowList]'s job succeeds.
 */
@Composable
internal fun ContentFilterPopoverSwitch(
    contentFilterEnabled: Boolean,
    host: String,
    subtitle: Int = R.string.content_filter_subtitle,
    trackersAllowList: TrackersAllowList,
    onSuccess: () -> Unit
) {
    val allowClickingSwitch = remember { mutableStateOf(true) }
    NeevaSwitch(
        primaryLabel = stringResource(id = R.string.content_filter),
        secondaryLabel = stringResource(id = subtitle),
        primaryColor = MaterialTheme.colorScheme.onSurface,
        isChecked = contentFilterEnabled,
        enabled = allowClickingSwitch.value,
        onCheckedChange = {
            // Disallow the switch from doing anything until the TrackersAllowList is updated.
            allowClickingSwitch.value = false

            val jobDidRun = trackersAllowList.toggleHostInAllowList(host = host) {
                allowClickingSwitch.value = true
                onSuccess()
            }

            if (!jobDidRun) {
                // The job couldn't be started because another was already in progress.
                // Re-enable the switch so the user can try again.
                allowClickingSwitch.value = true
            }
        }
    )
}
