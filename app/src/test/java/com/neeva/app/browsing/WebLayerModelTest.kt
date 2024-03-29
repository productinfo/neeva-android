// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.browsing

import android.app.Application
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neeva.app.BaseTest
import com.neeva.app.CoroutineScopeRule
import com.neeva.app.Dispatchers
import com.neeva.app.LoadingState
import com.neeva.app.NeevaConstants
import com.neeva.app.history.HistoryManager
import com.neeva.app.logging.ClientLogger
import com.neeva.app.publicsuffixlist.DomainProviderImpl
import com.neeva.app.settings.SettingsDataModel
import com.neeva.app.settings.SettingsToggle
import com.neeva.app.sharedprefs.SharedPrefFolder
import com.neeva.app.sharedprefs.SharedPreferencesModel
import com.neeva.app.userdata.IncognitoSessionToken
import com.neeva.app.userdata.LoginToken
import com.neeva.app.userdata.NeevaUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.chromium.weblayer.BrowsingDataType
import org.chromium.weblayer.Callback
import org.chromium.weblayer.Profile
import org.chromium.weblayer.WebLayer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class WebLayerModelTest : BaseTest() {
    @Rule
    @JvmField
    val coroutineScopeRule = CoroutineScopeRule()

    private lateinit var activityCallbackProvider: ActivityCallbackProvider
    private lateinit var application: Application
    private lateinit var browserWrapperFactory: BrowserWrapperFactory
    private lateinit var neevaConstants: NeevaConstants
    private lateinit var regularBrowserWrapper: RegularBrowserWrapper
    private lateinit var regularBrowserFragment: Fragment
    private lateinit var sharedPreferencesModel: SharedPreferencesModel
    private lateinit var webLayer: WebLayer

    @Mock private lateinit var activityCallbacks: ActivityCallbacks
    @Mock private lateinit var cacheCleaner: CacheCleaner
    @Mock private lateinit var clientLogger: ClientLogger
    @Mock private lateinit var domainProviderImpl: DomainProviderImpl
    @Mock private lateinit var historyManager: HistoryManager
    @Mock private lateinit var incognitoBrowserWrapper: IncognitoBrowserWrapper
    @Mock private lateinit var incognitoProfile: Profile
    @Mock private lateinit var incognitoSessionToken: IncognitoSessionToken
    @Mock private lateinit var loginToken: LoginToken
    @Mock private lateinit var neevaUser: NeevaUser
    @Mock private lateinit var regularProfile: Profile
    @Mock private lateinit var settingsDataModel: SettingsDataModel
    @Mock private lateinit var webLayerFactory: WebLayerFactory

    private lateinit var webLayerModel: WebLayerModel

    override fun setUp() {
        super.setUp()

        application = ApplicationProvider.getApplicationContext()

        activityCallbackProvider = mock {
            on { get() } doReturn activityCallbacks
        }

        regularBrowserFragment = mock {
            on { viewLifecycleOwnerLiveData } doReturn MutableLiveData(null)
        }

        regularBrowserWrapper = mock {
            on { createBrowserFragment() } doReturn regularBrowserFragment
        }

        browserWrapperFactory = mock {
            on { createRegularBrowser(any()) } doReturn regularBrowserWrapper
            on { createIncognitoBrowser(any(), any()) } doReturn incognitoBrowserWrapper
        }

        sharedPreferencesModel = SharedPreferencesModel(application)

        neevaConstants = NeevaConstants()

        loginToken = mock {
            on { cachedValue } doReturn "fake token"
            on { cachedValueFlow } doReturn MutableStateFlow("fake token")
        }

        neevaUser = mock {
            on { loginToken } doReturn loginToken
        }

        regularProfile = mock {
            on { cookieManager } doReturn mock()
        }

        webLayer = mock {
            on {
                getProfile(eq(RegularBrowserWrapper.NON_INCOGNITO_PROFILE_NAME))
            } doReturn regularProfile

            on {
                getIncognitoProfile(eq(IncognitoBrowserWrapper.INCOGNITO_PROFILE_NAME))
            } doReturn incognitoProfile
        }

        webLayerModel = WebLayerModel(
            activityCallbackProvider = activityCallbackProvider,
            browserWrapperFactory = browserWrapperFactory,
            webLayerFactory = webLayerFactory,
            application = application,
            cacheCleaner = cacheCleaner,
            domainProviderImpl = domainProviderImpl,
            historyManager = historyManager,
            dispatchers = Dispatchers(
                main = StandardTestDispatcher(coroutineScopeRule.scope.testScheduler),
                io = StandardTestDispatcher(coroutineScopeRule.scope.testScheduler)
            ),
            neevaUser = neevaUser,
            incognitoSessionToken = incognitoSessionToken,
            sharedPreferencesModel = sharedPreferencesModel,
            neevaConstants = neevaConstants,
            settingsDataModel = settingsDataModel,
            clientLogger = clientLogger,
            overrideCoroutineScope = coroutineScopeRule.scope
        )
    }

    private fun completeWebLayerInitialization() {
        val loadCallback = argumentCaptor<Callback<WebLayer>>()
        verify(webLayerFactory).load(loadCallback.capture())
        loadCallback.lastValue.onResult(webLayer)
        coroutineScopeRule.scope.advanceUntilIdle()
    }

    @Test
    fun testInitializationFlow() {
        expectThat(webLayerModel.initializationState.value).isEqualTo(LoadingState.LOADING)

        // Allow the coroutines to run, which should allow initialization to finish.
        coroutineScopeRule.scope.advanceUntilIdle()

        // Confirm that the other initialization tasks were run.
        runBlocking {
            verify(domainProviderImpl).initialize()
            verify(historyManager).pruneDatabase()
        }

        // Fire the callback WebLayerModel requires to store the WebLayer.
        completeWebLayerInitialization()

        // Because an Incognito Fragment couldn't be found, the cacheCleaner should have tried to
        // clean up everything.
        val runnableCaptor = argumentCaptor<Runnable>()
        verify(incognitoProfile).destroyAndDeleteDataFromDiskSoon(runnableCaptor.capture())
        runnableCaptor.lastValue.run()
        coroutineScopeRule.scope.advanceUntilIdle()

        runBlocking { verify(cacheCleaner).run() }

        coroutineScopeRule.scope.advanceUntilIdle()
        expectThat(webLayerModel.initializationState.value).isEqualTo(LoadingState.READY)

        val browsers = webLayerModel.browsersFlow.value
        expectThat(browsers.isCurrentlyIncognito).isFalse()
        expectThat(browsers.incognitoBrowserWrapper).isNull()
    }

    @Test
    fun testInitializationFlow_withIncognitoFragmentAndIncognitoSelected() {
        // SETUP
        // Signal that the Activity has an Incognito Fragment from a previous session.
        val incognitoFragment: Fragment = mock {
            on { viewLifecycleOwnerLiveData } doReturn MutableLiveData(null)
        }
        Mockito.`when`(activityCallbacks.getWebLayerFragment(eq(true)))
            .thenReturn(incognitoFragment)

        // Don't close the Incognito tabs on a profile switch.
        Mockito.`when`(
            settingsDataModel.getSettingsToggleValue(eq(SettingsToggle.CLOSE_INCOGNITO_TABS))
        ).thenReturn(false)

        // Say that the user was in Incognito before the app died.
        SharedPrefFolder.App.IsCurrentlyIncognito.set(sharedPreferencesModel, true)

        // TEST
        expectThat(webLayerModel.initializationState.value).isEqualTo(LoadingState.LOADING)

        // Allow the coroutines to run, which should allow initialization to finish.
        coroutineScopeRule.scope.advanceUntilIdle()

        // Confirm that the other initialization tasks were run.
        runBlocking {
            verify(domainProviderImpl).initialize()
            verify(historyManager).pruneDatabase()
        }

        // Fire the callback WebLayerModel requires to store the WebLayer.
        completeWebLayerInitialization()

        // CHECK EVERYTHING
        // Because an Incognito Fragment existed, we shouldn't have tried to clean up anything.
        runBlocking { verify(cacheCleaner, never()).run() }

        coroutineScopeRule.scope.advanceUntilIdle()
        expectThat(webLayerModel.initializationState.value).isEqualTo(LoadingState.READY)

        val browsers = webLayerModel.browsersFlow.value
        expectThat(browsers.isCurrentlyIncognito).isTrue()
        expectThat(browsers.incognitoBrowserWrapper).isNotNull()
    }

    @Test
    fun switchToProfile_whenSwitchingBetweenProfiles_reactsCorrectly() {
        completeWebLayerInitialization()
        verify(clientLogger, times(1)).onProfileSwitch(eq(false))

        webLayerModel.switchToProfile(true)
        coroutineScopeRule.scope.advanceUntilIdle()
        verify(clientLogger).onProfileSwitch(eq(true))
        expectThat(webLayerModel.browsersFlow.value.isCurrentlyIncognito).isEqualTo(true)
        expectThat(webLayerModel.currentBrowser).isEqualTo(incognitoBrowserWrapper)

        webLayerModel.switchToProfile(false)
        coroutineScopeRule.scope.advanceUntilIdle()
        verify(clientLogger, times(2)).onProfileSwitch(eq(false))
        expectThat(webLayerModel.browsersFlow.value.isCurrentlyIncognito).isEqualTo(false)
        expectThat(webLayerModel.currentBrowser).isEqualTo(regularBrowserWrapper)
    }

    @Test
    fun switchToProfile_withoutIncognitoProfile_unsetsIncognitoBrowser() {
        completeWebLayerInitialization()
        webLayerModel.switchToProfile(true)

        // The Incognito Browser should have been created.
        val onDestroyedCaptor = argumentCaptor<(IncognitoBrowserWrapper) -> Unit>()
        verify(clientLogger).onProfileSwitch(eq(true))
        verify(browserWrapperFactory).createIncognitoBrowser(any(), onDestroyedCaptor.capture())

        val browsersBefore = webLayerModel.browsersFlow.value
        expectThat(browsersBefore.isCurrentlyIncognito).isTrue()
        expectThat(browsersBefore.incognitoBrowserWrapper).isEqualTo(incognitoBrowserWrapper)

        // Signal that the Incognito browser was destroyed.
        onDestroyedCaptor.lastValue.invoke(incognitoBrowserWrapper)

        val browsersAfter = webLayerModel.browsersFlow.value
        expectThat(browsersAfter.incognitoBrowserWrapper).isEqualTo(null)
    }

    @Test
    fun switchToProfile_withoutIncognitoProfile_createsIncognitoOnlyIfNull() {
        completeWebLayerInitialization()
        webLayerModel.switchToProfile(true)

        // The Incognito Browser should have been created.
        verify(clientLogger).onProfileSwitch(eq(true))
        verify(browserWrapperFactory, times(1)).createIncognitoBrowser(any(), any())

        val browsersBefore = webLayerModel.browsersFlow.value
        expectThat(browsersBefore.isCurrentlyIncognito).isTrue()
        expectThat(browsersBefore.incognitoBrowserWrapper).isEqualTo(incognitoBrowserWrapper)

        // Switch to the regular profile.
        webLayerModel.switchToProfile(false)
        expectThat(webLayerModel.browsersFlow.value.isCurrentlyIncognito).isFalse()

        // Switch back to Incognito.
        webLayerModel.switchToProfile(true)
        expectThat(webLayerModel.browsersFlow.value.isCurrentlyIncognito).isTrue()

        // Verify that the previous Incognito BrowserWrapper was re-used.
        verify(browserWrapperFactory, times(1)).createIncognitoBrowser(any(), any())

        val browsersAfter = webLayerModel.browsersFlow.value
        expectThat(browsersAfter.incognitoBrowserWrapper).isEqualTo(incognitoBrowserWrapper)
        expectThat(browsersAfter.isCurrentlyIncognito).isTrue()
    }

    @Test
    fun switchToProfile_whenSettingToggledOn_closesTabsOnSwitch() {
        // Signal that the Activity has an Incognito Fragment from a previous session.
        val incognitoFragment: Fragment = mock {
            on { viewLifecycleOwnerLiveData } doReturn MutableLiveData(null)
        }
        Mockito.`when`(activityCallbacks.getWebLayerFragment(eq(true)))
            .thenReturn(incognitoFragment)

        completeWebLayerInitialization()

        // Because there is an incognito fragment open, we shouldn't have tried to delete anything.
        runBlocking { verify(cacheCleaner, never()).run() }
        verify(incognitoProfile, never()).destroyAndDeleteDataFromDiskSoon(any())

        webLayerModel.switchToProfile(true)

        // The Incognito Browser should have been created.
        verify(clientLogger).onProfileSwitch(eq(true))
        verify(browserWrapperFactory, times(1)).createIncognitoBrowser(any(), any())

        val browsersBefore = webLayerModel.browsersFlow.value
        expectThat(browsersBefore.isCurrentlyIncognito).isTrue()
        expectThat(browsersBefore.incognitoBrowserWrapper).isEqualTo(incognitoBrowserWrapper)

        Mockito.`when`(
            settingsDataModel.getSettingsToggleValue(eq(SettingsToggle.CLOSE_INCOGNITO_TABS))
        ).thenReturn(true)

        // Switch to the regular profile.
        webLayerModel.switchToProfile(false)
        coroutineScopeRule.advanceUntilIdle()

        expectThat(webLayerModel.browsersFlow.value.isCurrentlyIncognito).isFalse()
        expectThat(webLayerModel.browsersFlow.value.incognitoBrowserWrapper).isNull()
        verify(activityCallbacks).removeIncognitoFragment()

        val runnableCaptor = argumentCaptor<Runnable>()
        verify(incognitoProfile).destroyAndDeleteDataFromDiskSoon(runnableCaptor.capture())
        runnableCaptor.lastValue.run()
        coroutineScopeRule.advanceUntilIdle()

        runBlocking { verify(cacheCleaner).run() }
    }

    @Test
    fun switchToProfile_whenSettingToggledOff_closesTabsOnSwitch() {
        completeWebLayerInitialization()
        webLayerModel.switchToProfile(true)

        // The Incognito Browser should have been created.
        verify(clientLogger).onProfileSwitch(eq(true))
        verify(browserWrapperFactory, times(1)).createIncognitoBrowser(any(), any())

        val browsersBefore = webLayerModel.browsersFlow.value
        expectThat(browsersBefore.isCurrentlyIncognito).isTrue()
        expectThat(browsersBefore.incognitoBrowserWrapper).isEqualTo(incognitoBrowserWrapper)

        Mockito.`when`(
            settingsDataModel.getSettingsToggleValue(eq(SettingsToggle.CLOSE_INCOGNITO_TABS))
        ).thenReturn(false)

        // Switch to the regular profile.
        webLayerModel.switchToProfile(false)
        expectThat(webLayerModel.browsersFlow.value.isCurrentlyIncognito).isFalse()

        verify(incognitoBrowserWrapper, never()).closeAllTabs()
    }

    @Test
    fun clearBrowsingData_clearOnlyHistory() {
        completeWebLayerInitialization()
        val clearingOptions = mapOf(
            SettingsToggle.CLEAR_BROWSING_HISTORY to true,
            SettingsToggle.CLEAR_COOKIES to false,
            SettingsToggle.CLEAR_CACHE to false
        )

        webLayerModel.clearBrowsingData(clearingOptions, fromMillis = 0, toMillis = 2)

        verify(historyManager).clearHistory(eq(0))
        verify(regularProfile, never()).clearBrowsingData(
            eq(listOf<Int>().toIntArray()),
            eq(0),
            eq(2),
            any()
        )
    }

    @Test
    fun clearBrowsingData_clearOnlyCookies() {
        completeWebLayerInitialization()
        val clearingOptions = mapOf(
            SettingsToggle.CLEAR_BROWSING_HISTORY to false,
            SettingsToggle.CLEAR_COOKIES to true,
            SettingsToggle.CLEAR_CACHE to false
        )
        webLayerModel.clearBrowsingData(clearingOptions, fromMillis = 0, toMillis = 2)
        coroutineScopeRule.advanceUntilIdle()

        verify(historyManager, never()).clearHistory(any())
        verify(regularProfile).clearBrowsingData(
            eq(listOf(BrowsingDataType.COOKIES_AND_SITE_DATA).toIntArray()),
            eq(0),
            eq(2),
            any()
        )
    }

    @Test
    fun clearBrowsingData_clearOnlyCacheAndCookies() {
        completeWebLayerInitialization()
        val clearingOptions = mapOf(
            SettingsToggle.CLEAR_BROWSING_HISTORY to false,
            SettingsToggle.CLEAR_COOKIES to true,
            SettingsToggle.CLEAR_CACHE to true
        )

        webLayerModel.clearBrowsingData(clearingOptions, fromMillis = 0, toMillis = 2)
        coroutineScopeRule.advanceUntilIdle()

        verify(historyManager, never()).clearHistory(eq(0))
        verify(regularProfile).clearBrowsingData(
            eq(listOf(BrowsingDataType.COOKIES_AND_SITE_DATA, BrowsingDataType.CACHE).toIntArray()),
            eq(0),
            eq(2),
            any()
        )
    }

    @Test
    fun clearBrowsingData_clearAll() {
        completeWebLayerInitialization()
        val clearingOptions = mapOf(
            SettingsToggle.CLEAR_BROWSING_HISTORY to true,
            SettingsToggle.CLEAR_COOKIES to true,
            SettingsToggle.CLEAR_CACHE to true
        )
        webLayerModel.clearBrowsingData(clearingOptions, fromMillis = 0, toMillis = 2)
        coroutineScopeRule.advanceUntilIdle()

        verify(regularProfile).clearBrowsingData(
            eq(listOf(BrowsingDataType.COOKIES_AND_SITE_DATA, BrowsingDataType.CACHE).toIntArray()),
            eq(0),
            eq(2),
            any()
        )
    }

    @Test
    fun clearBrowsingData_clearNone() {
        completeWebLayerInitialization()
        val clearingOptions = mapOf(
            SettingsToggle.CLEAR_BROWSING_HISTORY to false,
            SettingsToggle.CLEAR_COOKIES to false,
            SettingsToggle.CLEAR_CACHE to false
        )
        webLayerModel.clearBrowsingData(clearingOptions, fromMillis = 0, toMillis = 2)
        verify(historyManager, never()).clearHistory(eq(0))
        verify(regularProfile, never()).clearBrowsingData(
            eq(listOf<Int>().toIntArray()),
            eq(0),
            eq(2),
            any()
        )
    }
}
