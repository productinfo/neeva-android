// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.suggestions

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.neeva.app.R
import com.neeva.app.ui.theme.NeevaTheme
import com.neeva.app.ui.widgets.RowActionIconParams
import com.neeva.app.ui.widgets.RowActionStartIconParams

@Composable
fun QueryNavSuggestionRow(
    query: String,
    description: String? = null,
    imageURL: String? = null,
    drawableID: Int = R.drawable.ic_baseline_search_24,
    drawableTint: Color = Color.Unspecified,
    onTapRow: () -> Unit,
    onEditUrl: (() -> Unit)? = null
) {
    NavSuggestionRow(
        primaryLabel = query,
        onTapRow = { onTapRow.invoke() },
        secondaryLabel = description,
        actionIconParams = onEditUrl?.let {
            RowActionIconParams(
                onTapAction = onEditUrl,
                actionType = RowActionIconParams.ActionType.REFINE,
                contentDescription = stringResource(R.string.edit_suggested_query, query)
            )
        },
        iconParams = RowActionStartIconParams(
            faviconBitmap = null,
            imageURL = imageURL,
            drawableID = drawableID,
            drawableTint = drawableTint
        )
    )
}

@Preview(name = "Image URL non-null, 1x font size")
@Preview(name = "Image URL non-null, 2x font size", fontScale = 2.0f)
@Composable
fun QuerySuggestion_PreviewWithImageUrl() {
    NeevaTheme {
        QueryNavSuggestionRow(
            query = "search query",
            description = "Suggestion description",
            imageURL = "https://www.neeva.com/favicon.png",
            drawableID = R.drawable.ic_baseline_search_24,
            drawableTint = Color.LightGray,
            onTapRow = {},
            onEditUrl = {}
        )
    }
}

@Preview(name = "1x font size")
@Preview(name = "2x font size", fontScale = 2.0f)
@Composable
fun QuerySuggestion_PreviewNoImageUrl() {
    NeevaTheme {
        QueryNavSuggestionRow(
            query = "search query",
            description = "Suggestion description",
            imageURL = null,
            drawableID = R.drawable.ic_baseline_search_24,
            drawableTint = Color.LightGray,
            onTapRow = {},
            onEditUrl = {}
        )
    }
}

@Preview(name = "No description, 1x font size")
@Preview(name = "No description, 2x font size", fontScale = 2.0f)
@Composable
fun QuerySuggestion_PreviewNoImageUrlNoDescription() {
    NeevaTheme {
        QueryNavSuggestionRow(
            query = "search query",
            description = null,
            imageURL = null,
            drawableID = R.drawable.ic_baseline_search_24,
            drawableTint = Color.LightGray,
            onTapRow = {},
            onEditUrl = {}
        )
    }
}

@Preview(name = "No image URL, 1x font size")
@Preview(name = "No image URL, 2x font size", fontScale = 2.0f)
@Composable
fun QuerySuggestion_PreviewNoImageUrlNoEdit() {
    NeevaTheme {
        QueryNavSuggestionRow(
            query = "search query",
            description = "Suggestion description",
            imageURL = null,
            drawableID = R.drawable.ic_baseline_search_24,
            drawableTint = Color.LightGray,
            onTapRow = {},
            onEditUrl = null
        )
    }
}

@Preview(name = "Uneditable, no description, 1x font size")
@Preview(name = "Uneditable, no description, 2x font size", fontScale = 2.0f)
@Composable
fun QuerySuggestion_PreviewNoImageUrlNoDescriptionNoEdit() {
    NeevaTheme {
        QueryNavSuggestionRow(
            query = "search query",
            description = null,
            imageURL = null,
            drawableID = R.drawable.ic_baseline_search_24,
            onTapRow = {},
            onEditUrl = null
        )
    }
}
