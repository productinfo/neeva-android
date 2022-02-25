package com.neeva.app.browsing

import android.net.Uri
import com.neeva.app.Dispatchers
import com.neeva.app.NeevaConstants
import com.neeva.app.spaces.SpaceStore
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.chromium.weblayer.NavigateParams
import org.chromium.weblayer.Navigation
import org.chromium.weblayer.NavigationCallback
import org.chromium.weblayer.Tab
import org.chromium.weblayer.TabCallback

/** Monitors changes to the [Browser]'s active tab and emits values related to it. */
class ActiveTabModelImpl(
    private val spaceStore: SpaceStore? = null,
    val coroutineScope: CoroutineScope,
    val dispatchers: Dispatchers,
    private val tabCreator: TabCreator
) : ActiveTabModel {
    private val _urlFlow = MutableStateFlow(Uri.EMPTY)
    override val urlFlow: StateFlow<Uri> = _urlFlow

    override val currentUrlInSpaceFlow: StateFlow<Boolean> =
        spaceStore
            ?.stateFlow
            ?.filter { it == SpaceStore.State.READY }
            ?.combine(_urlFlow) { _: SpaceStore.State, url: Uri ->
                spaceStore.spaceStoreContainsUrl(url)
            }
            ?.flowOn(dispatchers.io)
            ?.stateIn(coroutineScope, SharingStarted.Lazily, false)
            ?: MutableStateFlow(false)

    private val _titleFlow = MutableStateFlow("")
    override val titleFlow: StateFlow<String> = _titleFlow

    private val _navigationInfoFlow = MutableStateFlow(ActiveTabModel.NavigationInfo())
    override val navigationInfoFlow: StateFlow<ActiveTabModel.NavigationInfo> = _navigationInfoFlow

    private val _progressFlow = MutableStateFlow(100)
    override val progressFlow: StateFlow<Int> = _progressFlow

    private val _displayedText = MutableStateFlow("")
    override val displayedText: StateFlow<String> = _displayedText

    private val _isShowingQuery = MutableStateFlow(false)
    override val isShowingQuery: StateFlow<Boolean> = _isShowingQuery

    /** Tracks which tab is currently active. */
    internal val activeTabFlow = MutableStateFlow<Tab?>(null)
    internal val activeTab: Tab? get() = activeTabFlow.value

    internal fun onActiveTabChanged(newActiveTab: Tab?) {
        val previousTab = activeTabFlow.value
        previousTab?.apply {
            unregisterTabCallback(selectedTabCallback)
            navigationController.unregisterNavigationCallback(selectedTabNavigationCallback)
        }

        // We don't have a way to update the load progress without monitoring the tab, so hide the
        // bar until the NavigationCallback fires.
        _progressFlow.value = 100

        activeTabFlow.value = newActiveTab
        newActiveTab?.apply {
            registerTabCallback(selectedTabCallback)
            navigationController.registerNavigationCallback(selectedTabNavigationCallback)
        }

        // Update all the state to account for the currently selected tab's information.
        updateNavigationInfo()
        updateUrl(newActiveTab?.currentDisplayUrl ?: Uri.EMPTY)
        _titleFlow.value = newActiveTab?.currentDisplayTitle ?: ""
    }

    fun reload() {
        activeTabFlow.value?.navigationController?.reload()
    }

    /** Don't call this directly.  Instead, use [BrowserWrapper.loadUrl]. */
    internal fun loadUrl(uri: Uri, newTab: Boolean = false, isViaIntent: Boolean = false) {
        if (newTab || activeTabFlow.value == null) {
            tabCreator.createTabWithUri(
                uri = uri,
                parentTabId = null,
                isViaIntent = isViaIntent
            )
            return
        }

        // Disable intent processing for urls typed in. Allows the user to navigate to app urls.
        val navigateParamsBuilder = NavigateParams.Builder().disableIntentProcessing()
        activeTabFlow.value?.navigationController?.navigate(uri, navigateParamsBuilder.build())
    }

    private fun updateUrl(uri: Uri) {
        val isNeevaSearch = uri.toString().startsWith(NeevaConstants.appSearchURL)
        val query = if (isNeevaSearch) uri.getQueryParameter("q") else null

        _urlFlow.value = uri
        _displayedText.value = query ?: uri.host ?: ""
        _isShowingQuery.value = query != null
    }

    private val selectedTabCallback: TabCallback = object : TabCallback() {
        override fun onVisibleUriChanged(uri: Uri) {
            updateUrl(uri)
        }

        override fun onTitleUpdated(title: String) {
            _titleFlow.value = title
        }
    }

    private val selectedTabNavigationCallback = object : NavigationCallback() {
        override fun onLoadProgressChanged(progress: Double) {
            _progressFlow.value = (100 * progress).roundToInt()
        }

        override fun onNavigationStarted(navigation: Navigation) {
            updateNavigationInfo()
        }

        override fun onNavigationCompleted(navigation: Navigation) {
            updateNavigationInfo()
        }
    }

    fun goBack() {
        if (activeTabFlow.value?.navigationController?.canGoBack() == true) {
            activeTabFlow.value?.navigationController?.goBack()
            updateNavigationInfo()
        }
    }

    fun goForward() {
        if (activeTabFlow.value?.navigationController?.canGoForward() == true) {
            activeTabFlow.value?.navigationController?.goForward()
            updateNavigationInfo()
        }
    }

    private fun updateNavigationInfo() {
        _navigationInfoFlow.value = ActiveTabModel.NavigationInfo(
            activeTabFlow.value?.navigationController?.canGoBack() ?: false,
            activeTabFlow.value?.navigationController?.canGoForward() ?: false
        )
    }
}