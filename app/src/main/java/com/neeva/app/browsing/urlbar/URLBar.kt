package com.neeva.app.browsing.urlbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neeva.app.LocalBrowserToolbarModel
import com.neeva.app.browsing.ActiveTabModel
import com.neeva.app.browsing.toolbar.PreviewBrowserToolbarModel
import com.neeva.app.ui.OneBooleanPreviewContainer
import com.neeva.app.ui.theme.Dimensions

@Composable
fun URLBar(modifier: Modifier = Modifier) {
    val browserToolbarModel = LocalBrowserToolbarModel.current
    val isIncognito = browserToolbarModel.isIncognito

    val urlBarModel = browserToolbarModel.urlBarModel
    val urlBarModelState = urlBarModel.stateFlow.collectAsState()
    val isEditing = urlBarModelState.value.isEditing
    val focusUrlBar = urlBarModelState.value.focusUrlBar

    val backgroundColor = if (isIncognito) {
        MaterialTheme.colorScheme.inverseSurface
    } else {
        MaterialTheme.colorScheme.surface
    }

    val foregroundColor = if (isIncognito) {
        MaterialTheme.colorScheme.inverseOnSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val placeholderColor = if (isIncognito) {
        MaterialTheme.colorScheme.inverseOnSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = backgroundColor,
        contentColor = foregroundColor,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        modifier = modifier.padding(vertical = Dimensions.PADDING_SMALL)
    ) {
        val childModifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)

        if (isEditing) {
            AutocompleteTextField(
                textFieldValue = urlBarModelState.value.textFieldValue,
                suggestionText = urlBarModelState.value.autocompleteSuggestionText,
                faviconBitmap = urlBarModelState.value.faviconBitmap,
                placeholderColor = placeholderColor,
                onLocationEdited = { urlBarModel.onLocationBarTextChanged(it) },
                onCleared = { urlBarModel.replaceLocationBarText("") },
                onLoadUrl = { browserToolbarModel.onLoadUrl(urlBarModelState.value) },
                onAcceptAutocompleteSuggestion = urlBarModel::acceptAutocompleteSuggestion,
                focusUrlBar = focusUrlBar,
                modifier = childModifier
            )
        } else {
            LocationLabel(
                placeholderColor = placeholderColor,
                modifier = childModifier.clickable { urlBarModel.showZeroQuery() }
            )
        }
    }
}

@Preview(locale = "en")
@Preview(locale = "en", fontScale = 2.0f)
@Preview(locale = "he")
@Composable
fun URLBar_Preview_Editing() {
    OneBooleanPreviewContainer { isIncognito ->
        CompositionLocalProvider(
            LocalBrowserToolbarModel provides PreviewBrowserToolbarModel(
                isIncognito = isIncognito,
                displayedInfo = ActiveTabModel.DisplayedInfo(
                    ActiveTabModel.DisplayMode.URL,
                    displayedText = "website.url"
                ),
                urlBarModelStateValue = URLBarModelState(
                    isEditing = true,
                    textFieldValue = TextFieldValue("https://www.example.com")
                )
            )
        ) {
            URLBar()
        }
    }
}

@Preview(locale = "en")
@Preview(locale = "en", fontScale = 2.0f)
@Preview(locale = "he")
@Composable
fun URLBar_Preview_EditingPlaceholder() {
    OneBooleanPreviewContainer { isIncognito ->
        CompositionLocalProvider(
            LocalBrowserToolbarModel provides PreviewBrowserToolbarModel(
                isIncognito = isIncognito,
                displayedInfo = ActiveTabModel.DisplayedInfo(
                    ActiveTabModel.DisplayMode.URL,
                    displayedText = "website.url"
                ),
                urlBarModelStateValue = URLBarModelState(
                    isEditing = true
                )
            )
        ) {
            URLBar()
        }
    }
}
