package com.neeva.app.browsing

import android.net.Uri
import com.neeva.app.BaseTest
import com.neeva.app.CoroutineScopeRule
import com.neeva.app.urlbar.URLBarModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class URLBarModelTest: BaseTest() {
    @Rule @JvmField
    val coroutineScopeRule = CoroutineScopeRule()

    @Mock
    lateinit var onTextChanged: (String) -> Unit

    private lateinit var urlFlow: MutableStateFlow<Uri>
    private lateinit var activeTabModel: ActiveTabModel
    private lateinit var model: URLBarModel

    private val urlBarModelText: String
        get() = model.textFieldValue.value.text

    override fun setUp() {
        super.setUp()
        urlFlow = MutableStateFlow(Uri.EMPTY)

        activeTabModel = mock()
        Mockito.`when`(activeTabModel.urlFlow).thenReturn(urlFlow)

        model = URLBarModel(coroutineScopeRule.scope, activeTabModel, onTextChanged)
    }

    @Test
    fun init_collectsUrlFlow() {
        urlFlow.value = Uri.parse("https://www.reddit.com/r/android")
        coroutineScopeRule.scope.advanceUntilIdle()
        expectThat(urlBarModelText).isEqualTo("reddit.com")
        expectThat(model.showLock.value).isEqualTo(true)
        verify(onTextChanged, times(1)).invoke(eq("reddit.com"))

        urlFlow.value = Uri.parse("http://news.google.com/")
        coroutineScopeRule.scope.advanceUntilIdle()
        expectThat(urlBarModelText).isEqualTo("google.com")
        expectThat(model.showLock.value).isEqualTo(false)
        verify(onTextChanged, times(1)).invoke(eq("google.com"))

        TestResult
    }

    @Test
    fun loadUrl_withoutLazyTab() {
        val uri = Uri.parse("https://www.reddit.com/r/android")
        model.loadUrl(uri)
        verify(activeTabModel, times(1)).loadUrl(eq(uri), eq(false))
    }

    @Test
    fun loadUrl_withLazyTab() {
        // Load the bar with a non-empty string.
        urlFlow.value = Uri.parse("https://news.google.com")
        coroutineScopeRule.scope.advanceUntilIdle()
        expectThat(urlBarModelText).isEqualTo("google.com")

        // Open a lazy tab.
        model.openLazyTab()
        expectThat(urlBarModelText).isEqualTo("")

        // Loading the URL should send it to a new tab.
        val uri = Uri.parse("https://www.reddit.com/r/android")
        model.loadUrl(uri)
        verify(activeTabModel, times(1)).loadUrl(eq(uri), eq(true))
    }

    @Test
    fun reload() {
        model.reload()
        verify(activeTabModel, times(1)).reload()
    }

    @Test
    fun replaceLocationBarText() {
        // Focus the URL bar so that it can be edited.  This should normally be called when the
        // Composable representing the URL bar triggers it.
        model.onFocusChanged(true)
        model.replaceLocationBarText("random query")
        expectThat(urlBarModelText).isEqualTo("random query")

        model.replaceLocationBarText("query text")
        expectThat(urlBarModelText).isEqualTo("query text")
    }

    @Test
    fun onFocusChanged_withLazyTabAndThenUnfocusing_stopsLazyTab() {
        model.openLazyTab()
        expectThat(model.isLazyTab.value).isEqualTo(true)
        model.onFocusChanged(false)
        expectThat(model.isLazyTab.value).isEqualTo(false)
    }

    @Test
    fun onFocusChanged() {
        // Load the bar with a non-empty string.
        urlFlow.value = Uri.parse("https://news.google.com")
        coroutineScopeRule.scope.advanceUntilIdle()
        expectThat(urlBarModelText).isEqualTo("google.com")

        // When the bar is focused, remove whatever text was being displayed.
        model.onFocusChanged(true)
        expectThat(urlBarModelText).isEqualTo("")

        model.replaceLocationBarText("reddit.com/r/android")
        expectThat(urlBarModelText).isEqualTo("reddit.com/r/android")

        // When the bar is unfocused, it should return to showing the webpage domain.
        model.onFocusChanged(false)
        expectThat(urlBarModelText).isEqualTo("google.com")
    }
}