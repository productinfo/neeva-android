package com.neeva.app.browsing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.neeva.app.Dispatchers
import com.neeva.app.NeevaConstants
import com.neeva.app.R
import com.neeva.app.ToolbarConfiguration
import com.neeva.app.browsing.findinpage.FindInPageModel
import com.neeva.app.browsing.findinpage.FindInPageModelImpl
import com.neeva.app.browsing.urlbar.URLBarModel
import com.neeva.app.browsing.urlbar.URLBarModelImpl
import com.neeva.app.cookiecutter.CookieCutterModel
import com.neeva.app.cookiecutter.CookieCutterModelImpl
import com.neeva.app.cookiecutter.ScriptInjectionManager
import com.neeva.app.cookiecutter.TrackersAllowList
import com.neeva.app.history.HistoryManager
import com.neeva.app.publicsuffixlist.DomainProvider
import com.neeva.app.settings.SettingsDataModel
import com.neeva.app.settings.SettingsToggle
import com.neeva.app.spaces.SpaceStore
import com.neeva.app.storage.TabScreenshotManager
import com.neeva.app.storage.favicons.FaviconCache
import com.neeva.app.suggestions.SuggestionsModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.chromium.weblayer.Browser
import org.chromium.weblayer.BrowserControlsOffsetCallback
import org.chromium.weblayer.BrowserEmbeddabilityMode
import org.chromium.weblayer.BrowserRestoreCallback
import org.chromium.weblayer.NewTabType
import org.chromium.weblayer.OpenUrlCallback
import org.chromium.weblayer.PageInfoDisplayOptions
import org.chromium.weblayer.Profile
import org.chromium.weblayer.Tab
import org.chromium.weblayer.TabListCallback

abstract class BaseBrowserWrapper internal constructor(
    override val isIncognito: Boolean,
    protected val appContext: Context,
    protected val coroutineScope: CoroutineScope,
    protected val dispatchers: Dispatchers,
    protected val activityCallbackProvider: ActivityCallbackProvider,
    override val suggestionsModel: SuggestionsModel?,
    final override val faviconCache: FaviconCache,
    protected val spaceStore: SpaceStore?,
    private val tabList: TabList,
    private val _activeTabModelImpl: ActiveTabModelImpl,
    private val _urlBarModel: URLBarModelImpl,
    private val _findInPageModel: FindInPageModelImpl,
    private val historyManager: HistoryManager?,
    private val tabScreenshotManager: TabScreenshotManager,
    private val domainProvider: DomainProvider,
    protected val neevaConstants: NeevaConstants,
    private val scriptInjectionManager: ScriptInjectionManager,
    private val settingsDataModel: SettingsDataModel,
    override val cookieCutterModel: CookieCutterModel
) : BrowserWrapper, FaviconCache.ProfileProvider {
    /**
     * Constructor used to create a BaseBrowserWrapper that automatically creates various internal
     * classes.
     *
     * Tests should use the main constructor directly and pass in mocks for the
     * [ActiveTabModelImpl], [URLBarModelImpl], and whatever else the test needs.
     */
    constructor(
        isIncognito: Boolean,
        appContext: Context,
        coroutineScope: CoroutineScope,
        dispatchers: Dispatchers,
        activityCallbackProvider: ActivityCallbackProvider,
        suggestionsModel: SuggestionsModel?,
        faviconCache: FaviconCache,
        spaceStore: SpaceStore?,
        historyManager: HistoryManager?,
        tabScreenshotManager: TabScreenshotManager,
        domainProvider: DomainProvider,
        neevaConstants: NeevaConstants,
        scriptInjectionManager: ScriptInjectionManager,
        settingsDataModel: SettingsDataModel,
        trackerAllowList: TrackersAllowList,
        tabList: TabList = TabList()
    ) : this(
        isIncognito = isIncognito,
        appContext = appContext,
        coroutineScope = coroutineScope,
        dispatchers = dispatchers,
        activityCallbackProvider = activityCallbackProvider,
        suggestionsModel = suggestionsModel,
        faviconCache = faviconCache,
        spaceStore = spaceStore,
        tabList = tabList,
        _activeTabModelImpl = ActiveTabModelImpl(
            spaceStore = spaceStore,
            coroutineScope = coroutineScope,
            dispatchers = dispatchers,
            neevaConstants = neevaConstants,
            tabScreenshotManager = tabScreenshotManager,
            tabList = tabList
        ),
        _urlBarModel = URLBarModelImpl(
            suggestionFlow = suggestionsModel?.autocompleteSuggestionFlow ?: MutableStateFlow(null),
            appContext = appContext,
            coroutineScope = coroutineScope,
            dispatchers = dispatchers,
            faviconCache = faviconCache,
            neevaConstants = neevaConstants
        ),
        _findInPageModel = FindInPageModelImpl(),
        historyManager = historyManager,
        tabScreenshotManager = tabScreenshotManager,
        domainProvider = domainProvider,
        neevaConstants = neevaConstants,
        scriptInjectionManager = scriptInjectionManager,
        settingsDataModel = settingsDataModel,
        cookieCutterModel = CookieCutterModelImpl(
            trackerAllowList,
            coroutineScope,
            dispatchers,
            settingsDataModel
        )
    )

    private val tabCallbackMap: HashMap<String, TabCallbacks> = HashMap()

    final override val orderedTabList: StateFlow<List<TabInfo>> get() = tabList.orderedTabList

    /** Tracks if the active tab needs to be reloaded due to a renderer crash. */
    override val shouldDisplayCrashedTab: Flow<Boolean> =
        tabList.orderedTabList.map {
            it.any { tabInfo -> tabInfo.isSelected && tabInfo.isCrashed }
        }

    private val browserInitializationLock = Object()

    private var _fragment: Fragment? = null
    override fun getFragment(): Fragment? = _fragment
    override val fragmentViewLifecycleEventFlow = MutableStateFlow(Lifecycle.Event.ON_DESTROY)

    /**
     * Updated whenever the [Browser] is recreated.
     * If you don't need to monitor changes, you can directly access the [browser] field.
     */
    private val browserFlow = MutableStateFlow<Browser?>(null)

    protected val browser: Browser?
        get() = browserFlow.value?.takeUnless { it.isDestroyed }

    override val activeTabModel: ActiveTabModel get() = _activeTabModelImpl
    override val findInPageModel: FindInPageModel get() = _findInPageModel
    override val urlBarModel: URLBarModel get() = _urlBarModel

    /** Tracks whether the user needs to be kept in the CardGrid if they're on that screen. */
    final override val userMustStayInCardGridFlow: StateFlow<Boolean>

    private var tabListRestorer: BrowserRestoreCallback? = null

    private val _isLazyTabFlow = MutableStateFlow(false)
    override val isLazyTabFlow: StateFlow<Boolean> get() = _isLazyTabFlow

    /** Tracks when the WebLayer [Browser] has finished restoration and the [tabList] is ready. */
    private val isBrowserReady = CompletableDeferred<Boolean>()

    /** Tracks configuration changes that affect the bottom toolbar. */
    private var bottomToolbarExistsJob: Job? = null

    init {
        faviconCache.profileProvider = FaviconCache.ProfileProvider { getProfile() }

        userMustStayInCardGridFlow = orderedTabList
            .combine(_isLazyTabFlow) { tabs, isLazyTab ->
                // If the user has no open tabs (explicitly ignoring tabs being closed), keep them
                // in the card grid instead of sending them back out to the browser.
                tabs.filterNot { it.isClosing }.isEmpty() && !isLazyTab
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

        coroutineScope.launch {
            urlBarModel.isEditing.collectLatest { isEditing ->
                _isLazyTabFlow.value = _isLazyTabFlow.value && isEditing
            }
        }

        coroutineScope.launch {
            browserFlow.collectLatest { _urlBarModel.onBrowserChanged(it) }
        }
    }

    private var fullscreenCallback = FullscreenCallbackImpl(
        activityEnterFullscreen = { activityCallbackProvider.get()?.onEnterFullscreen() },
        activityExitFullscreen = { activityCallbackProvider.get()?.onExitFullscreen() }
    )

    private val tabListCallback = object : TabListCallback() {
        override fun onActiveTabChanged(activeTab: Tab?) {
            fullscreenCallback.exitFullscreen()
            changeActiveTab(activeTab)
        }

        override fun onTabRemoved(tab: Tab) {
            // Delete any screenshot that was taken for the tab.
            val tabId = tab.guid
            coroutineScope.launch(dispatchers.io) {
                tabScreenshotManager.deleteScreenshot(tabId)
            }

            // Remove the tab from our local state.
            val tabIndex = tabList.indexOf(tabId)
            val tabInfo = tabList.remove(tabId)

            // If the active tab is a child of the removed tab, update it so that we have the
            // correct navigation info.
            _activeTabModelImpl.onTabRemoved(tabId)

            // Remove all the callbacks associated with the tab to avoid any callbacks after the tab
            // gets destroyed.
            unregisterTabCallbacks(tabId)

            // If there is currently no tab marked as active, pick a new one.
            val activeTab = getActiveTab()
            if (activeTab == null) {
                setNextActiveTab(tabInfo, tabIndex)
            }
        }

        override fun onTabAdded(tab: Tab) {
            onNewTabAdded(tab)
            activityCallbackProvider.get()?.resetToolbarOffset()
        }

        override fun onWillDestroyBrowserAndAllTabs() {
            unregisterBrowserAndTabCallbacks()
            tabList.clear()
        }
    }

    private fun cleanCacheDirectory() {
        coroutineScope.launch(dispatchers.io) {
            // Clean up any unused tab thumbnails.
            val liveTabGuids = tabList.orderedTabList.value.map { it.id }
            tabScreenshotManager.cleanCacheDirectory(liveTabGuids)

            // Clean up any unused favicons.
            historyManager?.getAllFaviconUris()?.let { faviconCache.pruneCacheDirectory(it) }
        }
    }

    fun updateCookieCutterConfigAndRefreshTabs() {
        cookieCutterModel.updateTrackingProtectionConfiguration()
        val activeTab = getActiveTab()
        tabList.forEach {
            val tabCallbacks = tabCallbackMap[it] ?: return@forEach
            if (it == activeTab?.guid) {
                reloadAndUpdateStats(activeTab)
            } else {
                tabCallbacks.tabCookieCutterModel.reloadUponForeground = true
            }
        }
    }

    override fun reloadAfterContentFilterAllowListUpdate() {
        val activeTab = getActiveTab() ?: return
        val activeTabDomain =
            domainProvider.getRegisteredDomain(activeTab.currentDisplayUrl) ?: return
        tabList.forEach {
            val tabCallbacks = tabCallbackMap[it] ?: return@forEach
            val domain = domainProvider.getRegisteredDomain(tabCallbacks.tab.currentDisplayUrl)
            if (it == activeTab.guid) {
                reloadAndUpdateStats(activeTab)
            } else if (domain?.startsWith(activeTabDomain) == true) {
                tabCallbacks.tabCookieCutterModel.reloadUponForeground = true
            }
        }
    }

    private fun reloadAndUpdateStats(tab: Tab) {
        tab.navigationController.reload()
        tabCallbackMap[tab.guid]?.tabCookieCutterModel?.updateStats(tab.contentFilterStats)
    }

    private val browserControlsOffsetCallback = object : BrowserControlsOffsetCallback() {
        override fun onBottomViewOffsetChanged(offset: Int) {
            activityCallbackProvider.get()?.onBottomBarOffsetChanged(offset)
        }

        override fun onTopViewOffsetChanged(offset: Int) {
            activityCallbackProvider.get()?.onTopBarOffsetChanged(offset)
        }
    }

    /** Returns the [Browser] from the given [fragment]. */
    internal open fun getBrowserFromFragment(fragment: Fragment): Browser? {
        return Browser.fromFragment(fragment)
    }

    private fun getOrCreateBrowserFragment(): Fragment {
        val fragment = activityCallbackProvider.get()
            ?.getWebLayerFragment(isIncognito = isIncognito)
            ?: createBrowserFragment()

        // Monitor the Fragment's View's lifecycle so that we can detect when we can grab it.
        fragment.viewLifecycleOwnerLiveData.observeForever { viewLifecycleOwner ->
            if (viewLifecycleOwner == null) return@observeForever

            viewLifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    fragmentViewLifecycleEventFlow.value = event
                }
            })
        }

        return fragment
    }

    /**
     * Creates a Fragment that contains the [Browser] used to interface with WebLayer.
     *
     * This [Browser] that is created must be associated with the correct incognito or non-incognito
     * profile to avoid leaking state.
     */
    internal abstract fun createBrowserFragment(): Fragment

    /** Prepares the WebLayer Browser to interface with our app. */
    override fun createAndAttachBrowser(
        displaySize: Rect,
        toolbarConfiguration: StateFlow<ToolbarConfiguration>,
        fragmentAttacher: (fragment: Fragment, isIncognito: Boolean) -> Unit
    ) = synchronized(browserInitializationLock) {
        Log.d(TAG, "createAndAttachBrowser: incognito=$isIncognito browser=${browserFlow.value}")

        val fragment: Fragment = _fragment ?: getOrCreateBrowserFragment().also {
            _fragment = it

            // Keep the WebLayer instance across Activity restarts so that the Browser doesn't get
            // deleted when the configuration changes (e.g. the screen is rotated in fullscreen).
            @Suppress("DEPRECATION")
            it.retainInstance = true
        }

        fragmentAttacher(fragment, isIncognito)
        browserFlow.value = getBrowserFromFragment(fragment)
        Log.d(TAG, "createAndAttachBrowser: fragment=$_fragment browser=${browserFlow.value}")

        val browser = browserFlow.value ?: throw IllegalStateException()
        registerBrowserCallbacks(browser)
        browser.setMinimumSurfaceSize(displaySize.width(), displaySize.height())

        // Configure content filtering
        cookieCutterModel.setUpTrackingProtection(browser.profile.contentFilterManager)

        // Set the Views that WebLayer will use as placeholders for our toolbar.
        //
        // The [Job] usage is a workaround for https://github.com/neevaco/neeva-android/issues/452:
        // If we try to set the Browser's toolbar placeholders during initialization, we will
        // occasionally trigger an odd race condition that cause a Null Pointer Exception somewhere
        // deep in native WebLayer code.
        //
        // To work around this, we start a one-off Job that waits until we see that WebLayer has
        // registered a navigation, which we take as a signal that the [Browser] and its
        // [ContentViewRenderView] have been initialized.
        var toolbarJob: Job? = null
        toolbarJob = activeTabModel.navigationInfoFlow
            .filterNot { it.navigationListSize == 0 }
            .onEach {
                setToolbarPlaceholders(toolbarConfiguration)
                toolbarJob?.cancel()
                toolbarJob = null
            }
            .launchIn(coroutineScope)
    }

    /**
     * Sets the top and bottom toolbar placeholders that WebLayer will use to perform its toolbar
     * auto-hiding logic.
     *
     * We need to use placeholders because WebLayer does not work well with Jetpack Compose: they
     * don't get rendered at all, meaning that our toolbars are replaced with white boxes when the
     * toolbars aren't fully visible.
     *
     * Instead, [org.chromium.weblayer.BrowserControlsOffsetCallback] suggests that we pass in
     * placeholders that are the same height as the real toolbars and offset the toolbars whenever
     * the callback fires.
     */
    private fun setToolbarPlaceholders(toolbarConfiguration: StateFlow<ToolbarConfiguration>) {
        val browser = browserFlow.value ?: return

        val topControlsPlaceholder = View(appContext)
        browser.setTopView(topControlsPlaceholder)

        // The placeholder is now in the View hierarchy, so they now have LayoutParams that we can
        // set to our desired toolbar heights.
        topControlsPlaceholder.layoutParams.height =
            appContext.resources.getDimensionPixelSize(R.dimen.top_toolbar_height)
        topControlsPlaceholder.requestLayout()

        // Do the same for the bottom controls, if the screen is too narrow to use a single bar.
        val bottomToolbarPlaceholder = View(appContext).apply {
            // Set the ID so that we can find it during instrumentation tests.
            id = R.id.browser_bottom_toolbar_placeholder
        }
        val heightWhenVisible =
            appContext.resources.getDimensionPixelSize(R.dimen.bottom_toolbar_height)

        // Shrink the placeholder to 0px high when the keyboard is visible and reset it when the
        // keyboard is hidden.
        var keyboardJob: Job? = null
        val keyboardFlow: Flow<Boolean> = toolbarConfiguration
            .map { it.isKeyboardOpen }
            .distinctUntilChanged()
            .onEach { isKeyboardOpen ->
                val currentHeight = bottomToolbarPlaceholder.layoutParams.height
                val expectedHeight = when (isKeyboardOpen) {
                    true -> 0
                    false -> heightWhenVisible
                }
                if (currentHeight != expectedHeight) {
                    bottomToolbarPlaceholder.layoutParams.height = expectedHeight
                    bottomToolbarPlaceholder.requestLayout()
                }
            }

        // Keep track of whether the bottom toolbar should be visible and let WebLayer know.
        bottomToolbarExistsJob?.cancel()
        bottomToolbarExistsJob = toolbarConfiguration
            .map { it.useSingleBrowserToolbar }
            .distinctUntilChanged()
            .onEach { useSingleBrowserToolbar ->
                keyboardJob?.cancel()

                if (useSingleBrowserToolbar) {
                    browser.setBottomView(null)
                } else {
                    browser.setBottomView(bottomToolbarPlaceholder)
                    keyboardJob = keyboardFlow.launchIn(coroutineScope)
                }
            }
            .launchIn(coroutineScope)
            .apply {
                invokeOnCompletion { keyboardJob?.cancel() }
            }
    }

    /**
     * WebLayer automatically creates an empty tab in some situations (e.g. browser profile
     * creation).  Override this to do something with the tab, like navigate somewhere else or
     * close the tab entirely.
     */
    protected abstract fun onBlankTabCreated(tab: Tab)

    @CallSuper
    protected open fun registerBrowserCallbacks(browser: Browser): Boolean {
        if (tabListRestorer != null) {
            // If the tabListRestorer is non-null, we've previously registered callbacks on the
            // Browser.  This happens because the WebLayer Fragment survives Activity recreation.
            // Bail early to avoid adding additional copies of observers and callbacks.
            return false
        }

        val restorer = BrowserRestoreCallbackImpl(
            tabList = tabList,
            browser = browser,
            cleanCache = this::cleanCacheDirectory,
            onBlankTabCreated = this::onBlankTabCreated,
            onEmptyTabList = {
                createTabWithUri(
                    uri = Uri.parse(neevaConstants.appURL),
                    parentTabId = null,
                    isViaIntent = false,
                    stayInApp = true
                )
            },
            afterRestoreCompleted = { isBrowserReady.complete(true) }
        ).also {
            tabListRestorer = it
        }

        browser.registerTabListCallback(tabListCallback)
        browser.registerBrowserControlsOffsetCallback(browserControlsOffsetCallback)

        browser.profile.setTablessOpenUrlCallback(
            object : OpenUrlCallback() {
                override fun getBrowserForNewTab() = browser
                override fun onTabAdded(tab: Tab) = registerTabCallbacks(tab)
            }
        )

        // Let Neeva know that it's serving an Android client.
        browser.profile.cookieManager.setCookie(
            Uri.parse(neevaConstants.cookieURL),
            neevaConstants.browserTypeCookie.toString(),
            null
        )
        browser.profile.cookieManager.setCookie(
            Uri.parse(neevaConstants.cookieURL),
            neevaConstants.browserVersionCookie.toString(),
            null
        )

        browser.registerBrowserRestoreCallback(restorer)
        if (!browser.isRestoringPreviousState) {
            // WebLayer's Browser initialization can be finicky: If the [Browser] was already fully
            // restored when we added the callback, then our callback doesn't fire.  This can happen
            // if the app dies in the background, with WebLayer's Fragments automatically creating
            // the Browser before we have a chance to hook into it.
            // We work around this by manually calling onRestoreCompleted() if it's already done.
            restorer.onRestoreCompleted()
            changeActiveTab(browser.activeTab)
        }

        return true
    }

    /**
     * Change the active tab model and update any other state as needed (e.g., cookie cutter stat)
     *
     * @param tab Tab that will become active.
     */
    fun changeActiveTab(tab: Tab?) {
        _activeTabModelImpl.onActiveTabChanged(tab)
        tabList.updatedSelectedTab(tab?.guid)

        reregisterTabIfNecessary(tab)

        tab?.guid?.let { guid ->
            val tabCookieCutterModel = tabCallbackMap[guid]?.tabCookieCutterModel

            if (settingsDataModel.getSettingsToggleValue(SettingsToggle.TRACKING_PROTECTION)) {
                cookieCutterModel.trackingDataFlow.value =
                    tabCookieCutterModel?.currentTrackingData()
                cookieCutterModel.cookieNoticeBlockedFlow.value =
                    tabCookieCutterModel?.cookieNoticeBlocked ?: false
            }

            if (tabCookieCutterModel?.reloadUponForeground == true) {
                reloadAndUpdateStats(tab)
                tabCookieCutterModel.reloadUponForeground = false
            }
        }
    }

    override fun reregisterActiveTabIfNecessary() {
        reregisterTabIfNecessary(browserFlow.getActiveTab())
    }

    /**
     * Attempt at error correction: https://github.com/neevaco/neeva-android/issues/654
     *
     * If the Tab instance for the given Tab doesn't match the instance we're storing in the
     * TabCallbacks, re-register it so that all of our callbacks will actually fire.
     */
    private fun reregisterTabIfNecessary(tab: Tab?) {
        tab?.let { registerTabCallbacks(it) }
    }

    /**
     * Registers all the callbacks that are necessary for the Tab when it is opened.
     *
     * @param tab Tab that was just created.  It will be added to the [TabList] via another callback.
     * @param type Type of tab being opened.  Most cases result in foregrounding the tab.
     */
    private fun registerNewTab(tab: Tab, @NewTabType type: Int) {
        registerTabCallbacks(tab)

        when (type) {
            NewTabType.FOREGROUND_TAB,
            NewTabType.NEW_POPUP,
            NewTabType.NEW_WINDOW -> {
                selectTab(tab.guid)
            }

            else -> { /* Do nothing. */ }
        }
    }

    /**
     * Takes a newly created [tab] and registers all the callbacks we need to keep track of and
     * manipulate its state.
     */
    private fun registerTabCallbacks(tab: Tab) {
        if (tabCallbackMap[tab.guid] != null) {
            val previousTabInstance = tabCallbackMap[tab.guid]?.tab
            val previousTabBrowser = previousTabInstance?.browser
            if (previousTabInstance != tab || previousTabBrowser != tab.browser) {
                Log.w(TAG, "Replacing previous tab callbacks")
                Log.w(TAG, "\tTab was destroyed: ${previousTabInstance?.isDestroyed}")
                Log.w(TAG, "\tBrowser refs: ${tab.browser} vs ${previousTabInstance?.browser}")
                Log.w(TAG, "\tBrowser was destroyed: ${previousTabInstance?.browser?.isDestroyed}")
                unregisterTabCallbacks(tab.guid)
            } else {
                Log.d(TAG, "Keeping previous tab callbacks")
                return
            }
        }

        tabCallbackMap[tab.guid] = TabCallbacks(
            browserFlow = browserFlow,
            isIncognito = isIncognito,
            tab = tab,
            coroutineScope = coroutineScope,
            historyManager = historyManager,
            faviconCache = faviconCache,
            tabList = tabList,
            activityCallbackProvider = activityCallbackProvider,
            registerNewTab = this::registerNewTab,
            fullscreenCallback = fullscreenCallback,
            cookieCutterModel = cookieCutterModel,
            domainProvider = domainProvider,
            scriptInjectionManager = scriptInjectionManager,
        )
    }

    private fun unregisterTabCallbacks(tabId: String) {
        tabCallbackMap.remove(tabId)?.unregisterCallbacks()
    }

    /** Removes all the callbacks that are set up to interact with WebLayer. */
    @CallSuper
    open fun unregisterBrowserAndTabCallbacks() {
        synchronized(browserInitializationLock) {
            browser?.apply {
                unregisterTabListCallback(tabListCallback)
                unregisterBrowserControlsOffsetCallback(browserControlsOffsetCallback)
                tabListRestorer?.let { unregisterBrowserRestoreCallback(it) }

                profile.setTablessOpenUrlCallback(null)
            }

            // Avoid a ConcurrentModificationException by iterating on a copy of the keys rather
            // than the map itself.
            tabCallbackMap.keys.toList().forEach {
                unregisterTabCallbacks(it)
            }
            tabCallbackMap.clear()

            bottomToolbarExistsJob?.cancel()
            bottomToolbarExistsJob = null

            _fragment = null
            browserFlow.value = null
            tabListRestorer = null
        }
    }

    /** Creates a new tab and shows the given [uri]. */
    private fun createTabWithUri(
        uri: Uri,
        parentTabId: String?,
        isViaIntent: Boolean,
        stayInApp: Boolean
    ) {
        browser?.let {
            val tabOpenType = when {
                parentTabId != null -> TabInfo.TabOpenType.CHILD_TAB
                isViaIntent -> TabInfo.TabOpenType.VIA_INTENT
                else -> TabInfo.TabOpenType.DEFAULT
            }

            val newTab = it.createTab()
            newTab.navigate(uri, stayInApp)

            // onTabAdded should have been called by this point, allowing us to store the extra
            // information about the Tab.
            tabList.updateParentInfo(
                tab = newTab,
                parentTabId = parentTabId,
                tabOpenType = tabOpenType
            )

            selectTab(newTab.guid)
        }
    }

    private fun onNewTabAdded(tab: Tab) {
        tabList.add(tab)
        registerTabCallbacks(tab)
    }

    private fun setNextActiveTab(closedTabInfo: TabInfo?, closedTabIndex: Int) {
        // Send the user back to the tab's parent.
        val parentTabId = closedTabInfo?.data?.parentTabId
        if (browserFlow.setActiveTab(parentTabId)) {
            return
        }

        // Pick the tab immediately before the closed one.
        val newIndex = (closedTabIndex - 1).coerceAtLeast(0)
        val newIndexTabId = orderedTabList.value.getOrNull(newIndex)?.id
        if (browserFlow.setActiveTab(newIndexTabId)) {
            return
        }
    }

    override fun startClosingTab(id: String) {
        val tabIndex = tabList.indexOf(id)
        val tabInfo = tabList.getTabInfo(id)
        tabList.updateIsClosing(tabId = id, newIsClosing = true)

        // If the tab being closed is the active tab, mark a different tab as active.
        if (getActiveTab()?.guid == id) {
            setNextActiveTab(tabInfo, tabIndex)
        }
    }

    override fun cancelClosingTab(id: String) {
        tabList.updateIsClosing(id, newIsClosing = false)
    }

    override fun closeTab(id: String) {
        getTab(id)?.dispatchBeforeUnloadAndClose()
    }

    override fun closeAllTabs() {
        tabList.forEach { closeTab(it) }
    }

    override fun selectTab(id: String): Boolean {
        return browserFlow.setActiveTab(id)
    }

    override suspend fun restoreScreenshotOfTab(tabId: String): Bitmap? {
        return tabScreenshotManager.restoreScreenshot(tabId)
    }

    override fun takeScreenshotOfActiveTab(onCompleted: () -> Unit) {
        tabScreenshotManager.captureAndSaveScreenshot(getActiveTab(), onCompleted)
    }

    override fun showPageInfo() {
        browserFlow.value?.urlBarController?.showPageInfo(
            PageInfoDisplayOptions.builder().build()
        )
    }

    /**
     * Closes the active Tab if and only if it was opened via a VIEW Intent.
     * @return True if the tab was closed.
     */
    override fun closeActiveTabIfOpenedViaIntent(): Boolean {
        return getActiveTab()
            ?.let { activeTab ->
                val tabInfo = tabList.getTabInfo(activeTab.guid)
                tabInfo?.data?.openType
                    ?.takeIf { it == TabInfo.TabOpenType.VIA_INTENT }
                    ?.let {
                        closeTab(tabInfo.id)
                        true
                    }
            } ?: false
    }

    /**
     * Allows the user to use the URL bar and see suggestions without opening a tab until they
     * trigger a navigation.
     */
    override fun openLazyTab(focusUrlBar: Boolean) {
        _isLazyTabFlow.value = true
        urlBarModel.showZeroQuery(focusUrlBar)
    }

    /** Returns true if the [Browser] is maintaining no tabs. */
    override fun hasNoTabs(ignoreClosingTabs: Boolean): Boolean {
        return tabList.hasNoTabs(ignoreClosingTabs)
    }

    /** Returns true if the user should be forced to go to the card grid. */
    override fun userMustBeShownCardGrid(): Boolean = userMustStayInCardGridFlow.value

    override fun isFullscreen(): Boolean = fullscreenCallback.isFullscreen()
    override fun exitFullscreen(): Boolean = fullscreenCallback.exitFullscreen()

    /** Provides access to the WebLayer profile. */
    override fun getProfile(): Profile? = browser?.profile

    /** Returns a list of cookies split by key and values. */
    override fun getCookiePairs(uri: Uri, callback: (List<CookiePair>) -> Unit) {
        browser?.profile?.cookieManager?.apply {
            getCookie(uri) { cookiesString ->
                val cookies = cookiesString
                    .split(";")
                    .map { cookie ->
                        val parsedCookie = cookie.trim().split("=")
                        CookiePair(parsedCookie.first(), parsedCookie.last())
                    }
                callback(cookies)
            }
        }
    }

    // region: Active tab operations
    override fun goBack() = _activeTabModelImpl.goBack()
    override fun goForward() = _activeTabModelImpl.goForward()
    override fun reload() = _activeTabModelImpl.reload()
    override fun toggleViewDesktopSite() = _activeTabModelImpl.toggleViewDesktopSite()
    override fun resetOverscroll(action: Int) = _activeTabModelImpl.resetOverscroll(action)

    /**
     * Start a load of the given [uri].
     *
     * If the user is currently in the process of opening a new tab lazily, this will open a new Tab
     * with the URL.
     *
     * If the BrowserWrapper needs to redirect the user to another URI (e.g. if the user is
     * performing a search in Incognito for the first time), the load may be delayed by a network
     * call to get the updated URL.
     */
    override fun loadUrl(
        uri: Uri,
        inNewTab: Boolean,
        isViaIntent: Boolean,
        parentTabId: String?,
        stayInApp: Boolean,
        onLoadStarted: () -> Unit
    ) = coroutineScope.launch {
        // If you try to load a URL in a new tab before restoration has completed, the Browser may
        // drop the request on the floor.
        waitUntilBrowserIsReady()

        // Check if the user needs to be redirected somewhere else.
        val urlToLoad = if (shouldInterceptLoad(uri)) {
            getReplacementUrl(uri)
        } else {
            uri
        }

        if (inNewTab || getActiveTab() == null) {
            createTabWithUri(
                uri = urlToLoad,
                parentTabId = parentTabId,
                isViaIntent = isViaIntent,
                stayInApp = stayInApp
            )
        } else {
            _activeTabModelImpl.loadUrlInActiveTab(urlToLoad, stayInApp)
        }

        urlBarModel.clearFocus()
        onLoadStarted()
    }

    /** Asynchronously adds or removes the active tab from the space with given [spaceID]. */
    override fun modifySpace(spaceID: String, onOpenSpace: (String) -> Unit) {
        coroutineScope.launch(dispatchers.io) {
            spaceStore?.addOrRemoveFromSpace(
                spaceID = spaceID,
                url = activeTabModel.urlFlow.value,
                title = activeTabModel.titleFlow.value,
                onOpenSpace = onOpenSpace
            ) ?: Log.e(TAG, "Cannot modify space in Incognito mode")
        }
    }

    /** Dismisses any transient dialogs or popups that are covering the page. */
    override fun dismissTransientUi(): Boolean {
        return getActiveTab()?.dismissTransientUi() ?: false
    }

    override fun canGoBackward(): Boolean {
        return _activeTabModelImpl.navigationInfoFlow.value.canGoBackward
    }
    // endregion

    // region: Find In Page
    override fun showFindInPage() {
        getActiveTab()?.let { _findInPageModel.showFindInPage(it) }
    }
    // endregion

    override suspend fun allowScreenshots(allowScreenshots: Boolean) {
        val mode = if (allowScreenshots) {
            BrowserEmbeddabilityMode.SUPPORTED
        } else {
            BrowserEmbeddabilityMode.UNSUPPORTED
        }

        // https://github.com/neevaco/neeva-android/issues/600
        // As an odd side-effect result of putting WebLayer in the Compose hierarchy, the coroutine
        // gets hung up on the setEmbeddabilityMode() call until the user touches the screen.
        // Programmatically forcing a recomposition of the WebLayerContainer does nothing, so
        // there's some signal that the WebLayer Browser isn't getting to trigger the call.
        val result = suspendCoroutine<Boolean?> { continuation ->
            browser?.setEmbeddabilityMode(mode) {
                continuation.resume(it)
            } ?: run {
                continuation.resume(null)
            }
        }

        if (result != true) {
            Log.e(TAG, "Failed to update mode (allowScreenshots = $allowScreenshots)")
        } else {
            Log.d(TAG, "Successfully updated mode (allowScreenshots = $allowScreenshots)")
        }
    }

    /** Suspends the coroutine until the browser has finished initialization and restoration. */
    override suspend fun waitUntilBrowserIsReady() = isBrowserReady.await()

    private fun getActiveTab(): Tab? = browserFlow.getActiveTab()
    private fun getTab(id: String?): Tab? = browserFlow.getTab(id)

    companion object {
        private const val TAG = "BrowserWrapper"
    }
}
