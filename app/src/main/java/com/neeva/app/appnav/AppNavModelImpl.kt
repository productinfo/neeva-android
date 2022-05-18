package com.neeva.app.appnav

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.neeva.app.Dispatchers
import com.neeva.app.LocalEnvironment
import com.neeva.app.NeevaConstants
import com.neeva.app.R
import com.neeva.app.browsing.BrowserWrapper
import com.neeva.app.browsing.WebLayerModel
import com.neeva.app.logging.ClientLogger
import com.neeva.app.overflowmenu.OverflowMenuItemId
import com.neeva.app.spaces.AddToSpaceUI
import com.neeva.app.spaces.SpaceStore
import com.neeva.app.storage.entities.Space
import com.neeva.app.ui.SnackbarModel
import com.neeva.app.ui.widgets.overlay.OverlaySheetModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AppNavModelImpl(
    private val context: Context,
    override val navController: NavHostController,
    private val webLayerModel: WebLayerModel,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: Dispatchers,
    private val overlaySheetModel: OverlaySheetModel,
    private val snackbarModel: SnackbarModel,
    private val spaceStore: SpaceStore,
    private val clientLogger: ClientLogger,
    private val onTakeScreenshot: (callback: () -> Unit) -> Unit,
    private val neevaConstants: NeevaConstants
) : AppNavModel {
    private val _currentDestination = MutableStateFlow(navController.currentDestination)
    override val currentDestination: StateFlow<NavDestination?>
        get() = _currentDestination

    /** Keeps track of whether the back button should do anything. */
    private var backEnablingJob: Job? = null

    init {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            _currentDestination.value = destination
        }

        webLayerModel.initializedBrowserFlow
            .onEach { updateBackEnablingJob(it) }
            .flowOn(dispatchers.main)
            .launchIn(coroutineScope)
    }

    /**
     * Replaces the [Job] that is currently keeping track of whether or not the [navController]
     * should do anything when they hit back.
     *
     * Currently, this just prevents the user from backing out of the [CardGrid] destination when
     * the current [browserWrapper] has no tabs open.
     */
    private fun updateBackEnablingJob(browserWrapper: BrowserWrapper) {
        backEnablingJob?.cancel()
        backEnablingJob = browserWrapper.userMustStayInCardGridFlow
            .combine(currentDestination) { mustStay, currentDestination ->
                mustStay && currentDestination?.route == AppNavDestination.CARD_GRID.route
            }
            .onEach { userMustStay -> navController.enableOnBackPressed(!userMustStay) }
            .flowOn(dispatchers.main)
            .launchIn(coroutineScope)
    }

    /**
     * Navigates the user to a specific screen or context sheet, if they are not already viewing
     * that destination.
     *
     * The no-op is required to prevent the Accompanist Navigation Animation library (inexplicably)
     * animating from a destination to itself, which avoids having two instances of the same
     * Composable living in the hierarchy at the same time.
     */
    private fun show(
        destination: AppNavDestination,
        setOptions: NavOptionsBuilder.() -> Unit = {}
    ) {
        if (navController.currentDestination?.route == destination.route) return
        navController.navigate(destination.route) {
            launchSingleTop = true
            setOptions()
        }
    }

    override fun popBackStack() {
        navController.popBackStack()
    }

    /**
     * Show the browser view of the app.
     *
     * If the user has no tabs open, they are instead sent to the tab switcher unless
     * [forceUserToStayInCardGrid] is set to false.
     */
    override fun showBrowser(forceUserToStayInCardGrid: Boolean) {
        webLayerModel.currentBrowser.urlBarModel.clearFocus()

        show(AppNavDestination.BROWSER) {
            popUpTo(AppNavDestination.BROWSER.route) {
                inclusive = true
            }
        }

        if (webLayerModel.currentBrowser.userMustBeShownCardGrid() && forceUserToStayInCardGrid) {
            showCardGrid()
        }
    }

    override fun openLazyTab() {
        // Ordering is important here because showing the browser clears the focus of the URL bar
        // while opening a lazy tab requests the focus on the URL bar.
        showBrowser(forceUserToStayInCardGrid = false)
        webLayerModel.currentBrowser.openLazyTab()
    }

    override fun openUrl(url: Uri) {
        webLayerModel.currentBrowser.loadUrl(
            uri = url,
            inNewTab = true,
            onLoadStarted = this::showBrowser,
            stayInApp = true
        )
    }

    override fun openAndroidDefaultBrowserSettings() {
        safeStartActivityForIntent(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    override fun openUrlViaIntent(uri: Uri) {
        safeStartActivityForIntent(Intent(Intent.ACTION_VIEW, uri))
        showBrowser()
    }

    override fun safeStartActivityForIntent(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            snackbarModel.show(context.getString(R.string.error_generic))
            Log.e(TAG, "Failed to start Activity for $intent")
        }
    }

    override fun showCardGrid() = show(AppNavDestination.CARD_GRID)
    override fun showSettings() = show(AppNavDestination.SETTINGS)
    override fun showProfileSettings() = show(AppNavDestination.PROFILE_SETTINGS)
    override fun showClearBrowsingSettings() = show(AppNavDestination.CLEAR_BROWSING_SETTINGS)
    override fun showDefaultBrowserSettings() = show(AppNavDestination.SET_DEFAULT_BROWSER_SETTINGS)
    override fun showLocalFeatureFlagsPane() = show(AppNavDestination.LOCAL_FEATURE_FLAGS_SETTINGS)

    override fun showSpaceDetail(spaceID: String) {
        coroutineScope.launch {
            spaceStore.detailedSpaceIDFlow.emit(spaceID)
        }
        show(AppNavDestination.SPACE_DETAIL)
    }

    override fun showSignInFlow() {
        show(AppNavDestination.SIGN_IN_FLOW)
    }

    override fun showHistory() = show(AppNavDestination.HISTORY)

    override fun showFeedback() {
        onTakeScreenshot {
            show(AppNavDestination.FEEDBACK)
        }
    }

    override fun showHelp() {
        openUrl(Uri.parse(neevaConstants.appHelpCenterURL))
    }

    override fun shareCurrentPage() {
        val activeTabModel = webLayerModel.currentBrowser.activeTabModel
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"

            putExtra(Intent.EXTRA_TEXT, activeTabModel.urlFlow.value.toString())
            putExtra(Intent.EXTRA_TITLE, activeTabModel.titleFlow.value)
        }

        safeStartActivityForIntent(Intent.createChooser(sendIntent, null))
    }

    override fun shareSpace(space: Space) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"

            putExtra(Intent.EXTRA_TEXT, space.url(neevaConstants).toString())
            putExtra(Intent.EXTRA_TITLE, space.name)
        }

        safeStartActivityForIntent(Intent.createChooser(sendIntent, null))
    }

    override fun showAddToSpace() {
        overlaySheetModel.showOverlaySheet(titleResId = R.string.toolbar_save_to_space) {
            val spaceStore = LocalEnvironment.current.spaceStore
            val browserWrapper = webLayerModel.currentBrowser
            val activeTabModel = browserWrapper.activeTabModel

            LaunchedEffect(true) {
                spaceStore.refresh()
            }

            AddToSpaceUI(activeTabModel, spaceStore) { space ->
                browserWrapper.modifySpace(space.id)
                overlaySheetModel.hideOverlaySheet()
            }
        }
    }

    override fun onMenuItem(id: OverflowMenuItemId) {
        when (id) {
            OverflowMenuItemId.SETTINGS -> {
                showSettings()
            }

            OverflowMenuItemId.HISTORY -> {
                showHistory()
            }

            OverflowMenuItemId.FORWARD -> {
                webLayerModel.currentBrowser.goForward()
            }

            OverflowMenuItemId.RELOAD -> {
                webLayerModel.currentBrowser.reload()
            }

            OverflowMenuItemId.SHOW_PAGE_INFO -> {
                webLayerModel.currentBrowser.showPageInfo()
            }

            OverflowMenuItemId.FIND_IN_PAGE -> {
                webLayerModel.currentBrowser.showFindInPage()
            }

            OverflowMenuItemId.TOGGLE_DESKTOP_SITE -> {
                webLayerModel.currentBrowser.toggleViewDesktopSite()
            }

            OverflowMenuItemId.SUPPORT -> {
                showFeedback()
            }

            OverflowMenuItemId.UPDATE -> {
                openUrlViaIntent(neevaConstants.playStoreUri)
            }

            OverflowMenuItemId.DOWNLOADS -> {
                safeStartActivityForIntent(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            }

            OverflowMenuItemId.SPACES_WEBSITE -> {
                openUrl(Uri.parse(neevaConstants.appSpacesURL))
            }

            OverflowMenuItemId.CLOSE_ALL_TABS -> {
                // Handled elsewhere
            }

            OverflowMenuItemId.SEPARATOR -> {
                // Non-actionable.
            }
        }
    }

    companion object {
        val TAG = AppNavModelImpl::class.simpleName
    }
}
