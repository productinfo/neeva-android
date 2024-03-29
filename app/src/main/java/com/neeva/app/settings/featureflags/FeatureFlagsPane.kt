// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.settings.featureflags

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.neeva.app.settings.SettingsController
import com.neeva.app.settings.mockSettingsControllerImpl
import com.neeva.app.settings.sharedcomposables.SettingsPane
import com.neeva.app.ui.theme.NeevaTheme

@Composable
fun FeatureFlagsPane(
    settingsController: SettingsController
) {
    SettingsPane(settingsController, FeatureFlagsPaneData)
}

@Preview(name = "Feature Flags Pane, 1x font size", locale = "en")
@Preview(name = "Feature Flags Pane, 2x font size", locale = "en", fontScale = 2.0f)
@Preview(name = "Feature Flags Pane, RTL, 1x font size", locale = "he")
@Preview(name = "Feature Flags Pane, RTL, 2x font size", locale = "he", fontScale = 2.0f)
@Composable
fun ClearBrowsingSettings_Preview() {
    NeevaTheme {
        FeatureFlagsPane(mockSettingsControllerImpl)
    }
}

@Preview(name = "Feature Flags Pane Dark, 1x font size", locale = "en")
@Preview(name = "Feature Flags Pane Dark, 2x font size", locale = "en", fontScale = 2.0f)
@Preview(name = "Feature Flags Pane Dark, RTL, 1x font size", locale = "he")
@Preview(name = "Feature Flags Pane Dark, RTL, 2x font size", locale = "he", fontScale = 2.0f)
@Composable
fun ClearBrowsingSettings_Dark_Preview() {
    NeevaTheme(useDarkTheme = true) {
        FeatureFlagsPane(mockSettingsControllerImpl)
    }
}
