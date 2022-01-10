package com.neeva.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.apollographql.apollo3.ApolloClient
import com.neeva.app.browsing.BrowserCallbacks
import com.neeva.app.browsing.ContextMenuCreator
import com.neeva.app.browsing.WebLayerModel
import com.neeva.app.firstrun.FirstRun
import com.neeva.app.history.HistoryManager
import com.neeva.app.publicsuffixlist.DomainProviderImpl
import com.neeva.app.storage.HistoryDatabase
import com.neeva.app.storage.NeevaUser
import com.neeva.app.storage.SpaceStore
import com.neeva.app.ui.theme.NeevaTheme
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.chromium.weblayer.ContextMenuParams
import org.chromium.weblayer.Tab
import org.chromium.weblayer.WebLayer

@AndroidEntryPoint
class NeevaActivity : AppCompatActivity(), BrowserCallbacks {
    companion object {
        private const val NON_INCOGNITO_PROFILE_NAME = "DefaultProfile"
        private const val PERSISTENCE_ID = "Neeva_Browser"
        private const val EXTRA_START_IN_INCOGNITO = "EXTRA_START_IN_INCOGNITO"
        private const val WEBLAYER_FRAGMENT_TAG = "WebLayer Fragment"
    }

    @Inject lateinit var apolloClient: ApolloClient
    @Inject lateinit var spaceStore: SpaceStore
    @Inject lateinit var domainProviderImpl: DomainProviderImpl
    @Inject lateinit var historyDatabase: HistoryDatabase
    @Inject lateinit var historyManager: HistoryManager

    private val webModel by viewModels<WebLayerModel>()

    private val appNavModel by viewModels<AppNavModel> {
        AppNavModel.AppNavModelFactory(webModel.activeTabModel::loadUrl, spaceStore)
    }

    /**
     * View provided to WebLayer that allows us to receive information about when the bottom toolbar
     * needs to be scrolled off.  We provide a placeholder instead of the real view because WebLayer
     * appears to have a bug that prevents the bottom view from rendering correctly.
     * TODO(dan.alcantara): Revisit this once we move past WebLayer/Chromium v96.
     */
    private lateinit var bottomControlPlaceholder: View
    private lateinit var topControlPlaceholder: View
    private lateinit var bottomControls: View
    private lateinit var browserUI: View

    @SuppressLint("ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webModel.browserCallbacks = WeakReference(this)

        setContentView(R.layout.main)
        browserUI = findViewById<ComposeView>(R.id.browser_ui).apply {
            setContent {
                NeevaTheme {
                    Surface(color = MaterialTheme.colors.background) {
                        BrowserUI(
                            webModel.urlBarModel,
                            webModel.suggestionsModel,
                            webModel.activeTabModel
                        )
                    }
                }
            }
        }
        browserUI.background = null

        findViewById<ComposeView>(R.id.app_nav).setContent {
            NeevaTheme {
                Surface(color = Color.Transparent) {
                    AppNav(appNavModel, webModel, webModel.urlBarModel) { space ->
                        lifecycleScope.launch {
                            webModel.activeTabModel.modifySpace(space, apolloClient)
                            appNavModel.showBrowser()
                        }
                    }
                }
            }
        }
        topControlPlaceholder = layoutInflater.inflate(R.layout.fake_top_controls, null)
        bottomControlPlaceholder = layoutInflater.inflate(R.layout.fake_bottom_controls, null)

        bottomControls = findViewById<ComposeView>(R.id.tab_toolbar).apply {
            setContent {
                NeevaTheme {
                    Surface(color = MaterialTheme.colors.background) {
                        val isEditing: Boolean by webModel.urlBarModel.isEditing.collectAsState()
                        if (!isEditing) {
                            TabToolbar(
                                TabToolbarModel(
                                    appNavModel::showNeevaMenu,
                                    appNavModel::showAddToSpace,
                                    {
                                        webModel.onGridShown()
                                        appNavModel.showCardGrid()
                                    }
                                ),
                                webModel.activeTabModel
                            )
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.whenStarted {
                webModel.initializationState.collect {
                    if (it == LoadingState.READY) {
                        onWebLayerReady(savedInstanceState)
                    }
                }
            }
        }

        lifecycleScope.launchWhenCreated {
            NeevaUser.fetch(apolloClient)
        }

        lifecycleScope.launchWhenCreated {
            webModel.urlBarModel.isEditing.collect { isEditing ->
                if (isEditing) spaceStore.refresh()
            }
        }

        if (FirstRun.shouldShowFirstRun(this)) {
            appNavModel.showFirstRun()
            FirstRun.firstRunDone(this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        User.extractAuthTokenFromIntent(intent)?.let {
            User.setToken(this, it)
            webModel.onAuthTokenUpdated()
            appNavModel.showBrowser()
        }
    }

    private fun getOrCreateBrowserFragment(): Fragment {
        // Check if the Fragment was already created by a previous instance of the NeevaActivity.
        supportFragmentManager.findFragmentByTag(WEBLAYER_FRAGMENT_TAG)?.apply {
            return this
        }

        val fragment = WebLayer.createBrowserFragment(NON_INCOGNITO_PROFILE_NAME, PERSISTENCE_ID)
        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(R.id.weblayer, fragment, WEBLAYER_FRAGMENT_TAG)

        // Note the commitNow() instead of commit(). We want the fragment to get attached to
        // activity synchronously, so we can use all the functionality immediately. Otherwise we'd
        // have to wait until the commit is executed.
        transaction.commitNow()
        return fragment
    }

    private fun onWebLayerReady(savedInstanceState: Bundle?) {
        if (isFinishing || isDestroyed) return

        val fragment: Fragment = getOrCreateBrowserFragment()

        // Have WebLayer Shell retain the fragment instance to simulate the behavior of
        // external embedders (note that if this is changed, then WebLayer Shell should handle
        // rotations and resizes itself via its manifest, as otherwise the user loses all state
        // when the shell is rotated in the foreground).
        fragment.retainInstance = true

        webModel.onWebLayerReady(
            fragment,
            topControlPlaceholder,
            bottomControlPlaceholder,
            savedInstanceState
        )
    }

    override fun onBackPressed() {
        when {
            webModel.canExitFullscreen() -> {
                webModel.exitFullscreen()
            }

            appNavModel.state.value != AppNavState.BROWSER -> {
                appNavModel.showBrowser()
            }

            webModel.activeTabModel.navigationInfoFlow.value.canGoBackward -> {
                webModel.activeTabModel.goBack()
            }

            webModel.closeActiveChildTab() -> {
                return
            }

            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webModel.onSaveInstanceState(outState)
    }

    override fun onEnterFullscreen(): Int {
        // This avoids an extra resize.
        val attrs: WindowManager.LayoutParams = window.attributes
        attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
        window.attributes = attrs
        val decorView: View = window.decorView

        // Caching the system ui visibility is ok for shell, but likely not ok for real code.
        val systemVisibilityToRestore = decorView.systemUiVisibility
        decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                or View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        return systemVisibilityToRestore
    }

    override fun onExitFullscreen(systemVisibilityToRestore: Int) {
        val decorView: View = window.decorView
        decorView.systemUiVisibility = systemVisibilityToRestore
        val attrs: WindowManager.LayoutParams = window.attributes
        if (attrs.flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS != 0) {
            attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS.inv()
            window.attributes = attrs
        }
    }

    override fun bringToForeground() {
        appNavModel.showBrowser()

        val intent = Intent(this, NeevaActivity::class.java)
        intent.action = Intent.ACTION_MAIN
        startActivity(intent)
    }

    override fun showContextMenuForTab(contextMenuParams: ContextMenuParams, tab: Tab) {
        supportFragmentManager.findFragmentByTag(WEBLAYER_FRAGMENT_TAG)?.view?.apply {
            // Need to use the NeevaActivity as the context because the WebLayer View doesn't have
            // access to the correct resources.
            setOnCreateContextMenuListener(
                ContextMenuCreator(webModel, contextMenuParams, tab, this@NeevaActivity)
            )

            showContextMenu()
        }
    }

    override fun getDisplaySize(): Point {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val point = Point()
        display.getRealSize(point)
        return point
    }

    override fun onBottomBarOffsetChanged(offset: Int) {
        // Move the real bar when WebLayer says that the fake one is moving.
        bottomControls.translationY = offset.toFloat()
    }

    override fun onTopBarOffsetChanged(offset: Int) {
        // Move the real bar when WebLayer says that the fake one is moving.
        browserUI.translationY = offset.toFloat()
    }
}
