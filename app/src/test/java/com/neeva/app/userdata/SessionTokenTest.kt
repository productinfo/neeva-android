// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.userdata

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neeva.app.BaseTest
import com.neeva.app.CoroutineScopeRule
import com.neeva.app.NeevaConstants
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.chromium.weblayer.Browser
import org.chromium.weblayer.Callback
import org.chromium.weblayer.CookieChangeCause
import org.chromium.weblayer.CookieChangedCallback
import org.chromium.weblayer.CookieManager
import org.chromium.weblayer.Profile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.startsWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SessionTokenTest : BaseTest() {
    @get:Rule val coroutineScopeRule = CoroutineScopeRule()

    @MockK lateinit var cancelRunnable: Runnable

    private lateinit var neevaConstants: NeevaConstants
    private lateinit var sessionToken: SessionToken

    private lateinit var cookieManager: CookieManager
    private lateinit var profile: Profile
    private lateinit var browser: Browser

    private lateinit var server: MockWebServer
    private lateinit var serverUrl: String

    override fun setUp() {
        super.setUp()

        neevaConstants = NeevaConstants()

        cookieManager = mockk {
            every { addCookieChangedCallback(any(), any(), any()) } returns cancelRunnable

            every {
                getCookie(
                    eq(Uri.parse(neevaConstants.appURL)),
                    any()
                )
            } returns Unit
        }

        profile = mockk {
            every { cookieManager } returns this@SessionTokenTest.cookieManager
        }

        browser = mockk {
            every { isDestroyed } returns false
            every { profile } returns this@SessionTokenTest.profile
        }
    }

    private fun initializeSessionToken(
        initialCookieValue: String,
        serverResponse: String? = null
    ) {
        server = MockWebServer()
        serverResponse?.let { server.enqueue(MockResponse().setBody(it)) }
        server.start()
        serverUrl = server.url("/test/endpoint").toString()

        sessionToken = object : SessionToken(
            coroutineScope = coroutineScopeRule.scope,
            dispatchers = coroutineScopeRule.dispatchers,
            neevaConstants = neevaConstants,
            endpointURL = serverUrl,
            cookieName = neevaConstants.previewCookieKey
        ) {
            override var cachedValue: String = initialCookieValue

            override suspend fun processResponse(response: Response): Boolean {
                cachedValue = response.body?.string() ?: ""
                return true
            }

            override fun updateCachedCookie(newValue: String) {
                cachedValue = newValue
            }
        }
    }

    @Test
    fun cookieChangedCallback_withLiveBrowser_copiesCookieValue() {
        initializeSessionToken(initialCookieValue = "")
        val expectedCookieString = "${neevaConstants.previewCookieKey}=test cookie value"

        sessionToken.initializeCookieManager(browser, requestCookieIfEmpty = false)
        coroutineScopeRule.advanceUntilIdle()

        // The SessionToken should have hooked into the CookieManager.
        val callbackSlot = CapturingSlot<CookieChangedCallback>()
        verify {
            cookieManager.addCookieChangedCallback(
                eq(Uri.parse(neevaConstants.appURL)),
                eq(neevaConstants.previewCookieKey),
                capture(callbackSlot)
            )
        }

        // Fire the CookieChangedCallback so that the SessionToken asks the CookieManager for the
        // current state of the cookie.
        callbackSlot.captured.onCookieChanged("unused", CookieChangeCause.INSERTED)
        coroutineScopeRule.advanceUntilIdle()

        // Fire the callback with a cookie string that has two different cookies in it.
        val getCookieCallbackSlot = mutableListOf<Callback<String>>()
        verify {
            cookieManager.getCookie(
                eq(Uri.parse(neevaConstants.appURL)),
                capture(getCookieCallbackSlot)
            )
        }
        getCookieCallbackSlot.last().onResult(
            "unusedcookie=unusedvalue;$expectedCookieString"
        )
        coroutineScopeRule.advanceUntilIdle()

        // We should have received and cached the cookie.
        expectThat(sessionToken.cachedValue).isEqualTo("test cookie value")
    }

    @Test
    fun cookieChangedCallback_withLiveBrowserAndMissingCookie_clearsCachedCookie() {
        initializeSessionToken(initialCookieValue = "preset")

        val cookieSlot = CapturingSlot<String>()
        val setCookieCallbackSlot = CapturingSlot<Callback<Boolean>>()
        every {
            cookieManager.setCookie(
                eq(Uri.parse(neevaConstants.appURL)),
                capture(cookieSlot),
                capture(setCookieCallbackSlot)
            )
        } returns Unit

        // Expect that the Browser is initialized with the "preset" value.
        sessionToken.initializeCookieManager(browser, requestCookieIfEmpty = false)
        coroutineScopeRule.advanceUntilIdle()

        // Let the callback fire so that [initializeCookieManager] can complete.
        setCookieCallbackSlot.captured.onResult(true)
        coroutineScopeRule.advanceUntilIdle()

        // The SessionToken should have hooked into the CookieManager via a CookieChangedCallback.
        expectThat(cookieSlot.captured).startsWith("${neevaConstants.previewCookieKey}=preset")

        val callbackSlot = CapturingSlot<CookieChangedCallback>()
        verify {
            cookieManager.addCookieChangedCallback(
                eq(Uri.parse(neevaConstants.appURL)),
                eq(neevaConstants.previewCookieKey),
                capture(callbackSlot)
            )
        }

        // Fire the CookieChangedCallback so that the SessionToken asks the CookieManager for the
        // current state of the cookie.
        callbackSlot.captured.onCookieChanged("unused", CookieChangeCause.INSERTED)
        coroutineScopeRule.advanceUntilIdle()

        // Fire the callback with a cookie string that doesn't have the session cookie.
        val getCookieCallbackSlot = mutableListOf<Callback<String>>()
        verify {
            cookieManager.getCookie(
                eq(Uri.parse(neevaConstants.appURL)),
                capture(getCookieCallbackSlot)
            )
        }
        getCookieCallbackSlot.last().onResult("unusedcookie=unusedvalue")
        coroutineScopeRule.advanceUntilIdle()

        // We should have been told no cookie exists and cleared it out.
        expectThat(sessionToken.cachedValue).isEmpty()
    }

    @Test
    fun cookieChangedCallback_withDeadBrowser_doesNothingInCookieChangedCallback() {
        initializeSessionToken(initialCookieValue = "")
        sessionToken.initializeCookieManager(browser, requestCookieIfEmpty = false)
        coroutineScopeRule.advanceUntilIdle()

        val getCookieCallback = CapturingSlot<Callback<String>>()
        verify(exactly = 1) {
            cookieManager.getCookie(
                eq(Uri.parse(neevaConstants.appURL)),
                capture(getCookieCallback)
            )
        }
        getCookieCallback.captured.onResult("")
        coroutineScopeRule.advanceUntilIdle()

        // The SessionToken should have hooked into the CookieManager.
        val callbackSlot = CapturingSlot<CookieChangedCallback>()
        verify {
            cookieManager.addCookieChangedCallback(
                eq(Uri.parse(neevaConstants.appURL)),
                eq(neevaConstants.previewCookieKey),
                capture(callbackSlot)
            )
        }

        // Say that the browser is dead.
        every { browser.isDestroyed } returns true

        // Fire the CookieChangedCallback so that the SessionToken asks the CookieManager for the
        // current state of the cookie.
        callbackSlot.captured.onCookieChanged("unused", CookieChangeCause.INSERTED)
        coroutineScopeRule.advanceUntilIdle()

        // We shouldn't have tried to do anything with the CookieManager after the browser died.
        verify(exactly = 1) {
            cookieManager.getCookie(
                eq(Uri.parse(neevaConstants.appURL)),
                any()
            )
        }
        verify(exactly = 0) { cookieManager.setCookie(any(), any(), any()) }
    }

    @Test
    fun requestNewCookie_withValidResponse_savesCookie() {
        // Initialize the cookie with an empty value so that we try to fetch one.
        initializeSessionToken(initialCookieValue = "", serverResponse = "response processed")
        sessionToken.initializeCookieManager(browser, requestCookieIfEmpty = true)
        coroutineScopeRule.advanceUntilIdle()

        // Fire the callback with a cookie string that doesn't have the session cookie in it.
        // After the callback is fired, we'll expect the Browser to be updated with the new value.
        val getCookieCallbackSlot = CapturingSlot<Callback<String>>()
        verify {
            cookieManager.getCookie(
                eq(Uri.parse(neevaConstants.appURL)),
                capture(getCookieCallbackSlot)
            )
        }
        every {
            cookieManager.setCookie(
                eq(Uri.parse(neevaConstants.appURL)),
                eq("${neevaConstants.previewCookieKey}=response processed"),
                any()
            )
        } returns Unit
        getCookieCallbackSlot.captured.onResult("unusedcookie=unusedvalue;")
        coroutineScopeRule.advanceUntilIdle()

        // Confirm that we hit the right endpoint.
        val recordedRequest = server.takeRequest()
        expectThat(recordedRequest.requestUrl?.toString()).isEqualTo(serverUrl)

        // The cookie should be set correctly.
        expectThat(sessionToken.cachedValue).isEqualTo("response processed")
    }

    @Test
    fun requestNewCookie_withNoResponse_doesNothing() {
        // Initialize the cookie with an empty value so that we try to fetch one.
        initializeSessionToken(initialCookieValue = "", serverResponse = null)
        sessionToken.initializeCookieManager(browser, requestCookieIfEmpty = true)
        coroutineScopeRule.advanceUntilIdle()

        // Fire the callback with a cookie string that doesn't have the session cookie in it.
        // After the callback is fired, we'll expect the Browser to be updated with the new value.
        val getCookieCallbackSlot = CapturingSlot<Callback<String>>()
        verify {
            cookieManager.getCookie(
                eq(Uri.parse(neevaConstants.appURL)),
                capture(getCookieCallbackSlot)
            )
        }
        getCookieCallbackSlot.captured.onResult("unusedcookie=unusedvalue;")
        coroutineScopeRule.advanceUntilIdle()

        // Confirm that we hit the right endpoint.
        val recordedRequest = server.takeRequest()
        expectThat(recordedRequest.requestUrl?.toString()).isEqualTo(serverUrl)

        // The cookie should still be empty.
        expectThat(sessionToken.cachedValue).isEqualTo("")
    }

    @Test
    fun getCurrentCookieValue_ifCookieIsEmpty_requestsCookie() {
        // Initialize the cookie with an empty value so that we try to fetch one when needed.
        initializeSessionToken(initialCookieValue = "", serverResponse = "response processed")
        sessionToken.initializeCookieManager(browser, requestCookieIfEmpty = false)
        coroutineScopeRule.advanceUntilIdle()

        // The cookie should still be empty.
        expectThat(sessionToken.cachedValue).isEqualTo("")

        // Call getCurrentCookieValue, which should perform a network request to get the cookie.
        coroutineScopeRule.scope.launch {
            sessionToken.getOrFetchCookie()
        }
        coroutineScopeRule.advanceUntilIdle()

        // Return a useless cookie from the Browser by firing the callback.
        val getCookieCallbacks = mutableListOf<Callback<String>>()
        verify {
            cookieManager.getCookie(
                eq(Uri.parse(neevaConstants.appURL)),
                capture(getCookieCallbacks)
            )
        }
        getCookieCallbacks.last().onResult("unusedcookie=unusedvalue;")
        coroutineScopeRule.advanceUntilIdle()

        // A network request should have caused a new cookie to be set.
        expectThat(sessionToken.cachedValue).isEqualTo("response processed")
    }
}
