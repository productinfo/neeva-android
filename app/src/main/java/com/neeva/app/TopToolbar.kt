package com.neeva.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import com.neeva.app.browsing.findinpage.FindInPageToolbar
import com.neeva.app.urlbar.URLBar

@Composable
fun TopToolbar(topOffset: Float) {
    // Top controls: URL bar, Suggestions, Zero Query, ...
    val topOffsetDp = with(LocalDensity.current) { topOffset.toDp() }
    TopToolbar(
        modifier = Modifier
            .offset(y = topOffsetDp)
            .background(MaterialTheme.colorScheme.background)
    )
}

@Composable
fun TopToolbar(modifier: Modifier) {
    val browserWrapper = LocalBrowserWrapper.current
    val findInPageModel = browserWrapper.findInPageModel
    val findInPageInfo by findInPageModel.findInPageInfo.collectAsState()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.top_toolbar_height))
    ) {
        if (findInPageInfo.text != null) {
            FindInPageToolbar(
                findInPageInfo = findInPageInfo,
                onUpdateQuery = { findInPageModel.updateFindInPageQuery(it) },
                onScrollToResult = { forward -> findInPageModel.scrollToFindInPageResult(forward) }
            )
        } else {
            URLBar()
        }
    }
}
