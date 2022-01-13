package com.neeva.app.card

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.neeva.app.AppNavModel
import com.neeva.app.AppNavState
import com.neeva.app.R
import com.neeva.app.browsing.BrowserWrapper
import com.neeva.app.browsing.TabInfo
import com.neeva.app.storage.Favicon
import com.neeva.app.ui.BooleanPreviewParameterProvider
import com.neeva.app.ui.theme.NeevaTheme
import com.neeva.app.widgets.Button
import com.neeva.app.widgets.ComposableSingletonEntryPoint
import dagger.hilt.EntryPoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CardsContainer(
    appNavModel: AppNavModel,
    browserWrapper: BrowserWrapper
) {
    val urlBarModel = browserWrapper.urlBarModel

    val historyManager = EntryPoints
        .get(LocalContext.current.applicationContext, ComposableSingletonEntryPoint::class.java)
        .historyManager()

    val state: AppNavState by appNavModel.state.collectAsState()

    val cardGridListener = object : CardGridListener {
        override fun onSelectTab(tab: TabInfo) {
            browserWrapper.selectTab(tab)
            appNavModel.showBrowser()
        }

        override fun onCloseTab(tab: TabInfo) {
            browserWrapper.closeTab(tab)
        }

        override fun onOpenLazyTab() {
            urlBarModel.openLazyTab()
            appNavModel.showBrowser()
        }

        override fun onDone() {
            appNavModel.showBrowser()
        }

        override fun getFaviconFlow(uri: Uri?): Flow<Favicon?> {
            return historyManager.getFaviconFlow(uri)
        }

        override fun onSwitchToIncognitoProfile() {
            appNavModel.showTabSwitcher(useIncognito = true)
        }

        override fun onSwitchToRegularProfile() {
            appNavModel.showTabSwitcher(useIncognito = false)
        }
    }

    AnimatedVisibility(
        visible = state == AppNavState.CARD_GRID,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        // Reset the scroll state of the LazyVerticalGrid every time the active tab changes.
        // TODO(dan.alcantara): We'll need to investigate how this should work with tab groups
        //                      and child tabs.
        val tabs: List<TabInfo> by browserWrapper.orderedTabList.collectAsState()
        val activeTabIndex: Int = tabs.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        val listState = LazyListState(activeTabIndex)

        CardGrid(
            selectedScreen = if (browserWrapper.isIncognito) {
                SelectedScreen.INCOGNITO_TABS
            } else {
                SelectedScreen.REGULAR_TABS
            },
            listState = listState,
            cardGridListener = cardGridListener,
            tabs = tabs
        )
    }
}

interface CardGridListener {
    fun onSelectTab(tab: TabInfo)
    fun onCloseTab(tab: TabInfo)
    fun onOpenLazyTab()
    fun onDone()
    fun getFaviconFlow(uri: Uri?): Flow<Favicon?>
    fun onSwitchToIncognitoProfile()
    fun onSwitchToRegularProfile()
}

enum class SelectedScreen {
    INCOGNITO_TABS, REGULAR_TABS, SPACES
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardGrid(
    selectedScreen: SelectedScreen,
    listState: LazyListState,
    cardGridListener: CardGridListener,
    tabs: List<TabInfo>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        ModeSwitcher(selectedScreen = selectedScreen) {
            when (it) {
                SelectedScreen.REGULAR_TABS -> cardGridListener.onSwitchToRegularProfile()
                SelectedScreen.INCOGNITO_TABS -> cardGridListener.onSwitchToIncognitoProfile()
                SelectedScreen.SPACES -> TODO("Not implemented")
            }
        }

        LazyVerticalGrid(
            cells = GridCells.Fixed(2),
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(tabs) { tab ->
                val favicon: Favicon? by cardGridListener
                    .getFaviconFlow(tab.url)
                    .collectAsState(null)

                TabCard(
                    tab = tab,
                    faviconData = favicon,
                    onSelect = { cardGridListener.onSelectTab(tab) },
                    onClose = { cardGridListener.onCloseTab(tab) }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.LightGray)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                enabled = true,
                resID = R.drawable.ic_baseline_add_24,
                contentDescription = "New Tab"
            ) {
                cardGridListener.onOpenLazyTab()
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                enabled = true,
                resID = R.drawable.ic_baseline_close_24,
                contentDescription = "Done"
            ) {
                cardGridListener.onDone()
            }
        }
    }
}

class CardGridPreviews : BooleanPreviewParameterProvider<CardGridPreviews.Params>(2) {
    data class Params(
        val darkTheme: Boolean,
        val isIncognito: Boolean
    )

    override fun createParams(booleanArray: BooleanArray) = Params(
        darkTheme = booleanArray[0],
        isIncognito = booleanArray[1]
    )

    @Preview("1x", locale = "en")
    @Preview("2x", locale = "en", fontScale = 2.0f)
    @Preview("RTL, 1x", locale = "he")
    @Preview("RTL, 2x", locale = "he", fontScale = 2.0f)
    @Composable
    fun CardGrid_Preview(@PreviewParameter(CardGridPreviews::class) params: Params) {
        val darkTheme = params.darkTheme
        val selectedScreen = if (params.isIncognito) {
            SelectedScreen.INCOGNITO_TABS
        } else {
            SelectedScreen.REGULAR_TABS
        }

        NeevaTheme(darkTheme = darkTheme) {
            val listState = rememberLazyListState()
            val cardGridListener = object : CardGridListener {
                override fun onSelectTab(tab: TabInfo) {}
                override fun onCloseTab(tab: TabInfo) {}
                override fun onOpenLazyTab() {}
                override fun onDone() {}
                override fun getFaviconFlow(uri: Uri?): Flow<Favicon?> = flowOf(null)
                override fun onSwitchToIncognitoProfile() {}
                override fun onSwitchToRegularProfile() {}
            }

            val selectedTabIndex = 5
            val tabs = mutableListOf<TabInfo>()
            val tabTitles = listOf(
                stringResource(id = R.string.debug_long_string_primary),
                "short",
                "Amazon.com",
                "Ad-free, private search",
                "Some other amazing site",
                "Yep, another site",
                "Drink more Ovaltine"
            )
            tabTitles.forEachIndexed { i, title ->
                tabs.add(
                    TabInfo(
                        id = "tab $i",
                        parentTabId = null,
                        thumbnailUri = null,
                        url = Uri.parse("https://www.neeva.com/$i"),
                        title = title,
                        isSelected = i == selectedTabIndex
                    )
                )
            }

            CardGrid(selectedScreen, listState, cardGridListener, tabs)
        }
    }
}
