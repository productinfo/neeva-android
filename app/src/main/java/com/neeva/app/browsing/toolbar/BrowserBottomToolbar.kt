package com.neeva.app.browsing.toolbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neeva.app.LocalBrowserToolbarModel
import com.neeva.app.LocalBrowserWrapper
import com.neeva.app.R
import com.neeva.app.browsing.ActiveTabModel
import com.neeva.app.overflowmenu.OverflowMenuItemId
import com.neeva.app.ui.OneBooleanPreviewContainer
import kotlinx.coroutines.flow.MutableStateFlow

/** Contains all the controls available to the user in the bottom toolbar. */
@Composable
fun BrowserBottomToolbar(
    bottomOffset: Float,
    modifier: Modifier = Modifier
) {
    val browserWrapper = LocalBrowserWrapper.current
    val bottomOffsetDp = with(LocalDensity.current) { bottomOffset.toDp() }
    BrowserBottomToolbar(
        isIncognito = browserWrapper.isIncognito,
        modifier = modifier.offset(y = bottomOffsetDp)
    )
}

@Composable
fun BrowserBottomToolbar(
    isIncognito: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isIncognito) {
        MaterialTheme.colorScheme.inverseSurface
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        color = backgroundColor,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.bottom_toolbar_height))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            BackButton(
                color = contentColorFor(backgroundColor),
                modifier = Modifier.weight(1.0f)
            )

            ShareButton(
                modifier = Modifier.weight(1.0f)
            )

            AddToSpaceButton(
                modifier = Modifier.weight(1.0f)
            )

            TabSwitcherButton(
                modifier = Modifier.weight(1.0f)
            )
        }
    }
}

@Preview("Default, LTR", locale = "en")
@Preview("Default, RTL", locale = "he")
@Composable
internal fun BottomToolbarPreview_Regular() {
    OneBooleanPreviewContainer { isIncognito ->
        CompositionLocalProvider(LocalBrowserToolbarModel provides PreviewBrowserToolbarModel()) {
            BrowserBottomToolbar(isIncognito = isIncognito)
        }
    }
}

@Preview("Can go backward, LTR", locale = "en")
@Preview("Can go backward, RTL", locale = "he")
@Composable
internal fun BottomToolbarPreview_CanGoBackward() {
    OneBooleanPreviewContainer { isIncognito ->
        CompositionLocalProvider(
            LocalBrowserToolbarModel provides PreviewBrowserToolbarModel(
                navigationInfo = ActiveTabModel.NavigationInfo(
                    canGoBackward = true
                ),
                spaceStoreHasUrl = false
            )
        ) {
            BrowserBottomToolbar(isIncognito = isIncognito)
        }
    }
}

@Preview("Space store has URL, LTR", locale = "en")
@Preview("Space store has URL, RTL", locale = "he")
@Composable
internal fun BottomToolbarPreview_SpaceStoreHasUrl() {
    OneBooleanPreviewContainer { isIncognito ->
        CompositionLocalProvider(
            LocalBrowserToolbarModel provides PreviewBrowserToolbarModel(
                navigationInfo = ActiveTabModel.NavigationInfo(
                    canGoBackward = false
                ),
                spaceStoreHasUrl = true
            )
        ) {
            BrowserBottomToolbar(isIncognito = isIncognito)
        }
    }
}

/** Empty [BrowserToolbarModel] that doesn't do anything. */
private class PreviewBrowserToolbarModel(
    navigationInfo: ActiveTabModel.NavigationInfo = ActiveTabModel.NavigationInfo(),
    spaceStoreHasUrl: Boolean = false
) : BrowserToolbarModel() {
    override fun goBack() {}
    override fun goForward() {}
    override fun reload() {}
    override fun share() {}
    override fun onAddToSpace() {}
    override fun onMenuItem(id: OverflowMenuItemId) {}
    override fun onTabSwitcher() {}

    override val navigationInfoFlow = MutableStateFlow(navigationInfo)
    override val spaceStoreHasUrlFlow = MutableStateFlow(spaceStoreHasUrl)
}