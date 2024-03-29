// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.browsing.toolbar

import androidx.compose.runtime.Composable
import com.neeva.app.ui.LandscapePreviews

@LandscapePreviews
@Composable
fun ToolbarPreview_Blank_Landscape() {
    ToolbarPreview_Blank(true)
}

@LandscapePreviews
@Composable
fun ToolbarPreview_Focus_Landscape() {
    ToolbarPreview_Focus(true)
}

@LandscapePreviews
@Composable
fun ToolbarPreview_Typing_Landscape() {
    ToolbarPreview_Typing(true)
}

@LandscapePreviews
@Composable
fun ToolbarPreview_Search_Landscape() {
    ToolbarPreview_Search(true)
}

@LandscapePreviews
@Composable
fun ToolbarPreview_Loading_Landscape() {
    ToolbarPreview_Loading(useSingleBrowserToolbar = true)
}
