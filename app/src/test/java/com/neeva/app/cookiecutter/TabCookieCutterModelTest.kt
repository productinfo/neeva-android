package com.neeva.app.cookiecutter

import androidx.compose.runtime.mutableStateOf
import com.neeva.app.BaseTest
import com.neeva.app.publicsuffixlist.DomainProviderImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.chromium.weblayer.Browser
import org.chromium.weblayer.FaviconFetcher
import org.chromium.weblayer.NavigationController
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * Tests that the TabCookieCutterModel updates the stats properly
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class TabCookieCutterModelTest : BaseTest() {
    private lateinit var model: TabCookieCutterModel
    private lateinit var domainProviderImpl: DomainProviderImpl

    @Mock
    private lateinit var browser: Browser

    override fun setUp() {
        super.setUp()
        val navigationController: NavigationController = mock()
        val faviconFetcher: FaviconFetcher = mock()

//        val mockTab: Tab = mock {
//            on { getGuid() } doReturn "tab guid 1"
//            on { getNavigationController() } doReturn navigationController
//            on { getBrowser() } doReturn browser
//            on { createFaviconFetcher(any()) } doReturn faviconFetcher
//        }

        domainProviderImpl = DomainProviderImpl(RuntimeEnvironment.getApplication())

        model = TabCookieCutterModel(
            browserFlow = MutableStateFlow(null),
            tabId = "tab guid 1",
            trackingDataFlow = MutableStateFlow(null),
            enableTrackingProtection = mutableStateOf(true),
            domainProvider = domainProviderImpl
        )
    }

    override fun tearDown() {
        super.tearDown()
    }

    @Test
    fun testTrackingStatsInModel() {
        model.updateStats(
            mapOf(
                "1emn.com" to 1,
                "accountkit.com" to 2,
                "ads-twitter.com" to 3
            )
        )
        val trackingData = model.currentTrackingData()
        expectThat(trackingData.numTrackers).isEqualTo(6)
        expectThat(trackingData.numDomains).isEqualTo(3)
        expectThat(trackingData.trackingEntities?.size).isEqualTo(3)

        model.resetStat()
        val emptyTrackingData = model.currentTrackingData()
        expectThat(emptyTrackingData.numTrackers).isEqualTo(0)
        expectThat(emptyTrackingData.numDomains).isEqualTo(0)
        expectThat(emptyTrackingData.trackingEntities?.size).isEqualTo(0)
    }
}