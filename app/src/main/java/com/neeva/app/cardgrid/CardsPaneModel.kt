// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.cardgrid

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.neeva.app.R
import com.neeva.app.appnav.AppNavModel
import com.neeva.app.browsing.BrowserWrapper
import com.neeva.app.browsing.TabInfo
import com.neeva.app.browsing.WebLayerModel
import com.neeva.app.cardgrid.tabs.TabContextMenu
import com.neeva.app.ui.PopupModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

interface CardsPaneModel {
    val selectedScreen: MutableState<SelectedScreen>
    val previousScreen: MutableState<SelectedScreen?>

    fun switchScreen(newSelectedScreen: SelectedScreen)
    fun showArchivedTabs()

    fun selectTab(browserWrapper: BrowserWrapper, tab: TabInfo)
    fun closeTab(browserWrapper: BrowserWrapper, tab: TabInfo)
    fun openLazyTab(browserWrapper: BrowserWrapper)
    fun closeAllTabs(browserWrapper: BrowserWrapper)

    fun selectSpace(browserWrapper: BrowserWrapper, spaceUrl: Uri)

    fun showContextMenu(browserWrapper: BrowserWrapper, tab: TabInfo)
}

class CardsPaneModelImpl(
    private val context: Context,
    private val webLayerModel: WebLayerModel,
    private val appNavModel: AppNavModel,
    private val popupModel: PopupModel,
    coroutineScope: CoroutineScope
) : CardsPaneModel {
    // Keep track of what screen is currently being viewed by the user.
    override val selectedScreen: MutableState<SelectedScreen> = mutableStateOf(
        if (webLayerModel.currentBrowser.isIncognito) {
            SelectedScreen.INCOGNITO_TABS
        } else {
            SelectedScreen.REGULAR_TABS
        }
    )

    override val previousScreen: MutableState<SelectedScreen?> = mutableStateOf(null)

    init {
        coroutineScope.launch {
            webLayerModel.initializedBrowserFlow.collectLatest {
                if (it.isIncognito) {
                    when (selectedScreen.value) {
                        SelectedScreen.INCOGNITO_TABS -> {
                            // Do nothing.
                        }

                        SelectedScreen.REGULAR_TABS, SelectedScreen.SPACES -> {
                            updateSelectedScreen(SelectedScreen.INCOGNITO_TABS)
                        }
                    }
                } else {
                    when (selectedScreen.value) {
                        SelectedScreen.INCOGNITO_TABS -> {
                            updateSelectedScreen(SelectedScreen.REGULAR_TABS)
                        }

                        SelectedScreen.REGULAR_TABS, SelectedScreen.SPACES -> {
                            // Do nothing.
                        }
                    }
                }
            }
        }
    }

    private fun updateSelectedScreen(newSelectedScreen: SelectedScreen) {
        previousScreen.value = selectedScreen.value
        selectedScreen.value = newSelectedScreen
    }

    override fun switchScreen(newSelectedScreen: SelectedScreen) {
        updateSelectedScreen(newSelectedScreen)

        when (newSelectedScreen) {
            SelectedScreen.REGULAR_TABS -> {
                webLayerModel.switchToProfile(useIncognito = false)
            }

            SelectedScreen.INCOGNITO_TABS -> {
                webLayerModel.switchToProfile(useIncognito = true)
            }

            SelectedScreen.SPACES -> {
                webLayerModel.switchToProfile(useIncognito = false)
            }
        }
    }

    override fun showArchivedTabs() {
        appNavModel.showArchivedTabs()
    }

    override fun selectTab(browserWrapper: BrowserWrapper, tab: TabInfo) {
        browserWrapper.selectTab(tab.id)
        appNavModel.showBrowser(forceUserToStayInCardGrid = false)
    }

    override fun closeTab(browserWrapper: BrowserWrapper, tab: TabInfo) {
        browserWrapper.startClosingTab(tab.id)
        popupModel.showSnackbar(
            message = context.getString(R.string.closed_tab, tab.title),
            actionLabel = context.getString(R.string.undo),
            onActionPerformed = {
                browserWrapper.cancelClosingTab(tab.id)
            },
            onDismissed = {
                browserWrapper.closeTab(tab.id)
            }
        )
    }

    override fun openLazyTab(browserWrapper: BrowserWrapper) {
        appNavModel.openLazyTab()
    }

    override fun closeAllTabs(browserWrapper: BrowserWrapper) {
        browserWrapper.closeAllTabs()
    }

    override fun selectSpace(browserWrapper: BrowserWrapper, spaceUrl: Uri) {
        val id = spaceUrl.pathSegments.last() ?: return
        appNavModel.showSpaceDetail(id)
    }

    override fun showContextMenu(browserWrapper: BrowserWrapper, tab: TabInfo) {
        popupModel.showContextMenu { onDismissRequested ->
            TabContextMenu(
                tab = tab,
                browserWrapper = browserWrapper,
                onDismissRequested = onDismissRequested
            )
        }
    }
}
