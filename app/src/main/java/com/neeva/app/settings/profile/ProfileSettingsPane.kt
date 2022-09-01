// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.settings.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.neeva.app.NeevaConstants
import com.neeva.app.settings.SettingsController
import com.neeva.app.settings.mockSettingsControllerImpl
import com.neeva.app.settings.sharedcomposables.SettingsPane
import com.neeva.app.ui.theme.NeevaTheme

@Composable
fun ProfileSettingsPane(settingsController: SettingsController, neevaConstants: NeevaConstants) {
    SettingsPane(settingsController, ProfileSettingsPaneData(neevaConstants))
}

@Preview(name = "Settings Profile, 1x font size", locale = "en")
@Preview(name = "Settings Profile, 2x font size", locale = "en", fontScale = 2.0f)
@Preview(name = "Settings Profile, RTL, 1x font size", locale = "he")
@Preview(name = "Settings Profile, RTL, 2x font size", locale = "he", fontScale = 2.0f)
@Composable
fun SettingsProfile_Preview() {
    NeevaTheme {
        ProfileSettingsPane(
            settingsController = mockSettingsControllerImpl,
            neevaConstants = NeevaConstants()
        )
    }
}

@Preview(name = "Settings Profile Dark, 1x font size", locale = "en")
@Preview(name = "Settings Profile Dark, 2x font size", locale = "en", fontScale = 2.0f)
@Preview(name = "Settings Profile Dark, RTL, 1x font size", locale = "he")
@Preview(name = "Settings Profile Dark, RTL, 2x font size", locale = "he", fontScale = 2.0f)
@Composable
fun SettingsProfile_Dark_Preview() {
    NeevaTheme(useDarkTheme = true) {
        ProfileSettingsPane(
            settingsController = mockSettingsControllerImpl,
            neevaConstants = NeevaConstants()
        )
    }
}
