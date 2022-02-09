package com.neeva.app

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import androidx.window.layout.WindowMetricsCalculator
import com.apollographql.apollo3.ApolloClient
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.neeva.app.appnav.AppNavDestination
import com.neeva.app.appnav.AppNavModel
import com.neeva.app.browsing.ActivityCallbacks
import com.neeva.app.browsing.ContextMenuCreator
import com.neeva.app.browsing.WebLayerModel
import com.neeva.app.firstrun.FirstRun
import com.neeva.app.history.HistoryManager
import com.neeva.app.settings.SettingsDataModel
import com.neeva.app.sharedprefs.SharedPreferencesModel
import com.neeva.app.spaces.SpaceStore
import com.neeva.app.storage.NeevaUser
import com.neeva.app.ui.theme.NeevaTheme
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.chromium.weblayer.ContextMenuParams
import org.chromium.weblayer.Tab

@AndroidEntryPoint
class NeevaActivity : AppCompatActivity(), ActivityCallbacks {
    companion object {
        // Tags for the WebLayer Fragments.  Lets us retrieve them via the FragmentManager.
        private const val TAG_REGULAR_PROFILE = "FRAGMENT_TAG_REGULAR_PROFILE"
        private const val TAG_INCOGNITO_PROFILE = "FRAGMENT_TAG_INCOGNITO_PROFILE"
    }

    @Inject lateinit var apolloClient: ApolloClient
    @Inject lateinit var spaceStore: SpaceStore
    @Inject lateinit var settingsDataModel: SettingsDataModel
    @Inject lateinit var sharedPreferencesModel: SharedPreferencesModel
    @Inject lateinit var neevaUser: NeevaUser
    @Inject lateinit var webModel: WebLayerModel
    @Inject lateinit var historyManager: HistoryManager
    @Inject lateinit var dispatchers: Dispatchers

    private lateinit var containerRegularProfile: View
    private lateinit var containerIncognitoProfile: View

    private val activityViewModel: NeevaActivityViewModel by viewModels {
        NeevaActivityViewModel.Factory(intent)
    }

    internal var appNavModel: AppNavModel? = null

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webModel.activityCallbacks = WeakReference(this)

        setContentView(R.layout.main)

        findViewById<ComposeView>(R.id.browser_ui).apply {
            setContent {
                val navController = rememberAnimatedNavController()

                appNavModel = remember(navController) {
                    AppNavModel(
                        navController = navController,
                        webLayerModel = webModel,
                        coroutineScope = lifecycleScope,
                        dispatchers = dispatchers
                    )
                }

                // Set up all the classes that'll need to be sent to all of the Composables.
                val environment = LocalEnvironmentState(
                    appNavModel = appNavModel!!,
                    settingsDataModel = settingsDataModel,
                    historyManager = historyManager,
                    dispatchers = dispatchers,
                    sharedPreferencesModel = sharedPreferencesModel,
                    neevaUser = neevaUser
                )

                NeevaTheme {
                    CompositionLocalProvider(LocalEnvironment provides environment) {
                        ActivityUI(
                            bottomControlOffset = activityViewModel.bottomControlOffset,
                            topControlOffset = activityViewModel.topControlOffset,
                            webLayerModel = webModel,
                            apolloClient = apolloClient
                        )
                    }
                }

                LaunchedEffect(appNavModel) {
                    // Refresh the user's Spaces whenever they try to add something to one.
                    appNavModel?.currentDestination?.collect {
                        if (it?.route == AppNavDestination.ADD_TO_SPACE.name) {
                            spaceStore.refresh()
                        }
                    }
                }

                LaunchedEffect(true) {
                    if (FirstRun.shouldShowFirstRun(
                            sharedPreferencesModel,
                            neevaUser.neevaUserToken
                        )
                    ) {
                        appNavModel!!.showFirstRun()
                        FirstRun.firstRunDone(sharedPreferencesModel)
                    }
                }
            }
        }

        containerRegularProfile = findViewById(R.id.weblayer_regular)
        containerIncognitoProfile = findViewById(R.id.weblayer_incognito)

        lifecycleScope.launch {
            lifecycle.whenStarted {
                webModel.initializationState
                    .combine(webModel.browserWrapperFlow) { loadingState, browserDelegate ->
                        Pair(loadingState, browserDelegate)
                    }
                    .stateIn(lifecycleScope)
                    .collect { (loadingState, _) ->
                        if (loadingState != LoadingState.READY) return@collect
                        prepareWebLayer()

                        // Check if there are any Intents that have URLs that need to be loaded.
                        activityViewModel.getPendingLaunchIntent()?.let { processIntent(intent) }
                    }
            }
        }

        lifecycleScope.launch {
            fetchNeevaUserInfo()
        }

        if (savedInstanceState != null && webModel.currentBrowser.isFullscreen()) {
            // If the activity was recreated because the user entered a fullscreen video or website,
            // hide the system bars.
            onEnterFullscreen()
        }
    }

    private suspend fun fetchNeevaUserInfo() {
        withContext(dispatchers.io) {
            neevaUser.fetch(apolloClient)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return

        if (Uri.parse(intent.dataString).scheme == "neeva") {
            NeevaUserToken.extractAuthTokenFromIntent(intent)?.let {
                neevaUser.neevaUserToken.setToken(it)
                webModel.onAuthTokenUpdated()
                appNavModel?.showBrowser()
                webModel.currentBrowser.activeTabModel.reload()
            }
        } else {
            intent.data?.let {
                webModel.currentBrowser.activeTabModel.loadUrl(
                    uri = it,
                    newTab = true,
                    isViaIntent = true
                )
            }
        }

        appNavModel?.showBrowser()
    }

    private fun prepareWebLayer() {
        when {
            webModel.initializationState.value != LoadingState.READY -> return
            isFinishing -> return
            isDestroyed -> return
        }

        val topControlPlaceholder = View(this)
        val bottomControlPlaceholder = View(this)
        webModel.onWebLayerReady(
            topControlPlaceholder,
            bottomControlPlaceholder,
            this::attachWebLayerFragment
        )
    }

    /**
     * Attach the given [Fragment] to the Activity, which allows creation of the [Browser].
     *
     * https://github.com/neevaco/neeva-android/issues/244: WebLayer Fragments do some unexpected
     * things while attached to the Activity.  Most egregiously, even if the Fragment's View is GONE
     * when the app is backgrounded, upon returning to the app WebLayer will automatically make the
     * web page visible again -- even if the parent View in the hierarchy is still marked as GONE.
     *
     * WebLayerShell avoids this issue entirely by forcing each Activity instance to maintain a
     * single profile at a time, but that doesn't work with how our tab switcher is designed to
     * allow switching between the two modes.  Until then, get the opposite Fragment's View
     * (literally) out of sight by translating it out of the way.
     *
     * The more ideal alternative would be to detach Fragments them when the user switches between
     * regular and incognito modes, but this triggers destruction of the Browser and Profile
     * associated with the Fragment, which causes all Incognito state to be lost.  This happens even
     * if we ask WebLayer to persist the state by giving it a specific profile name and
     * persistenceId when calling WebLayer.createIncognitoBrowserFragment(), which will require
     * further investigation.
     */
    private fun attachWebLayerFragment(fragment: Fragment, isIncognito: Boolean) {
        // Note the commitNow() instead of commit(). We want the fragment to get attached to
        // activity synchronously, so we can use all the functionality immediately. Otherwise we'd
        // have to wait until the commit is executed.
        val transaction = supportFragmentManager.beginTransaction()
        lateinit var hiddenContainer: View
        lateinit var visibleContainer: View
        if (isIncognito) {
            transaction.replace(R.id.weblayer_incognito, fragment, TAG_INCOGNITO_PROFILE)
            visibleContainer = containerIncognitoProfile
            hiddenContainer = containerRegularProfile
        } else {
            transaction.replace(R.id.weblayer_regular, fragment, TAG_REGULAR_PROFILE)
            visibleContainer = containerRegularProfile
            hiddenContainer = containerIncognitoProfile
        }

        visibleContainer.visibility = View.VISIBLE
        visibleContainer.translationX = 0f
        hiddenContainer.visibility = View.GONE
        hiddenContainer.translationX = getDisplaySize().width().toFloat()
        hiddenContainer.requestLayout()

        transaction.commitNow()
    }

    override fun onBackPressed() {
        val browserWrapper = webModel.currentBrowser

        when {
            browserWrapper.exitFullscreen() -> {
                return
            }

            browserWrapper.activeTabModel.activeTabFlow.value?.dismissTransientUi() == true -> {
                return
            }

            onBackPressedDispatcher.hasEnabledCallbacks() -> {
                onBackPressedDispatcher.onBackPressed()
            }

            browserWrapper.activeTabModel.navigationInfoFlow.value.canGoBackward -> {
                browserWrapper.activeTabModel.goBack()
            }

            browserWrapper.closeActiveChildTab() -> {
                // Closing the child tab will kick the user back to the parent tab, if possible.
                return
            }

            browserWrapper.closeActiveTabIfOpenedViaIntent() -> {
                // Let Android kick the user back to the calling app.
                super.onBackPressed()
            }

            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onEnterFullscreen() {
        val rootView: View = findViewById(android.R.id.content)
        rootView.keepScreenOn = true

        val controller = WindowInsetsControllerCompat(window, rootView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onExitFullscreen() {
        val rootView: View = findViewById(android.R.id.content)
        rootView.keepScreenOn = false

        val controller = WindowInsetsControllerCompat(window, rootView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun bringToForeground() {
        appNavModel?.showBrowser()

        val intent = Intent(this, NeevaActivity::class.java)
        intent.action = Intent.ACTION_MAIN
        startActivity(intent)
    }

    override fun showContextMenuForTab(contextMenuParams: ContextMenuParams, tab: Tab) {
        tab.takeUnless { it.isDestroyed }?.browser?.fragment?.view?.apply {
            // Need to use the NeevaActivity as the context because the WebLayer View doesn't have
            // access to the correct resources.
            setOnCreateContextMenuListener(
                ContextMenuCreator(
                    webModel.browserWrapperFlow.value,
                    contextMenuParams,
                    tab,
                    this@NeevaActivity
                )
            )

            showContextMenu()
        }
    }

    override fun getDisplaySize(): Rect {
        return WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this).bounds
    }

    override fun onBottomBarOffsetChanged(offset: Int) =
        activityViewModel.onBottomBarOffsetChanged(offset)

    override fun onTopBarOffsetChanged(offset: Int) =
        activityViewModel.onTopBarOffsetChanged(offset)

    override fun detachIncognitoFragment() {
        // Do a post to avoid a Fragment transaction while one is already occurring to remove the
        // Incognito fragment, which can happen if the user closed all their incognito tabs
        // manually.
        Handler(Looper.getMainLooper()).post {
            supportFragmentManager.findFragmentByTag(TAG_INCOGNITO_PROFILE)?.let { fragment ->
                supportFragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitNow()
            }
        }
    }
}
