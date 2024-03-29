// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.apollo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Operation
import com.neeva.app.BaseTest
import com.neeva.app.NeevaConstants
import com.neeva.app.SearchQuery
import com.neeva.app.userdata.IncognitoSessionToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue

@RunWith(AndroidJUnit4::class)
class IncognitoApolloWrapperTest : BaseTest() {
    @MockK lateinit var apolloClientWrapper: ApolloClientWrapper

    private lateinit var incognitoSessionToken: IncognitoSessionToken
    private lateinit var neevaConstants: NeevaConstants
    private lateinit var apolloWrapper: IncognitoApolloWrapper

    private var incognitoCookie: String = ""

    override fun setUp() {
        super.setUp()

        incognitoSessionToken = mockk {
            coEvery { getOrFetchCookie() } answers { incognitoCookie }
        }

        neevaConstants = NeevaConstants()
        apolloWrapper = IncognitoApolloWrapper(
            incognitoSessionToken = incognitoSessionToken,
            neevaConstants = neevaConstants,
            apolloClientWrapper = apolloClientWrapper
        )
    }

    @Test
    fun prepareForOperation_withSetToken_ignoresUserMustBeLoggedIn() {
        incognitoCookie = "not empty"

        expectThat(
            runBlocking { apolloWrapper.prepareForOperation(userMustBeLoggedIn = false) }
        ).isTrue()
        coVerify(exactly = 1) { incognitoSessionToken.getOrFetchCookie() }

        expectThat(
            runBlocking { apolloWrapper.prepareForOperation(userMustBeLoggedIn = true) }
        ).isTrue()
        coVerify(exactly = 2) { incognitoSessionToken.getOrFetchCookie() }
    }

    @Test
    fun prepareForOperation_withNoToken_checksLoginRequirement() {
        incognitoCookie = ""

        expectThat(
            runBlocking { apolloWrapper.prepareForOperation(userMustBeLoggedIn = false) }
        ).isTrue()
        coVerify(exactly = 1) { incognitoSessionToken.getOrFetchCookie() }

        expectThat(
            runBlocking { apolloWrapper.prepareForOperation(userMustBeLoggedIn = true) }
        ).isFalse()
        coVerify(exactly = 2) { incognitoSessionToken.getOrFetchCookie() }
    }

    @Test
    fun performQuery_withNoTokenSet_requestsTokenBeforeExecuting() {
        incognitoCookie = ""

        val response = mockk<ApolloResponse<SearchQuery.Data>> {
            every { hasErrors() } returns false
        }

        val apolloCall = mockk<ApolloCall<SearchQuery.Data>> {
            coEvery { execute() } returns response
        }

        every { apolloClientWrapper.query<SearchQuery.Data>(any()) } returns apolloCall
        coEvery { incognitoSessionToken.getOrFetchCookie() } answers {
            // Say that the cookie becomes valid when `getOrFetchCookie()` is called.
            incognitoCookie = "now valid cookie"
            incognitoCookie
        }

        runBlocking {
            val result = apolloWrapper.performQuery(
                SearchQuery(query = "query"),
                userMustBeLoggedIn = true
            )
            expectThat(result.response).isEqualTo(response)
            expectThat(result.exception).isNull()
        }

        coVerify(exactly = 1) { incognitoSessionToken.getOrFetchCookie() }
    }

    @Test
    fun performQuery_ifFailsToRetrieveToken_failsToPerformQuery() {
        val response = mockk<ApolloResponse<SearchQuery.Data>> {
            every { hasErrors() } returns false
        }

        val apolloOperation = mockk<Operation<SearchQuery.Data>> {}
        val apolloCall = mockk<ApolloCall<SearchQuery.Data>> {
            coEvery { execute() } returns response
            every { operation } returns apolloOperation
        }

        every { apolloClientWrapper.query<SearchQuery.Data>(any()) } returns apolloCall
        coEvery { incognitoSessionToken.getOrFetchCookie() } answers {
            // Say that the cookie stays invalid.
            incognitoCookie = ""
            incognitoCookie
        }

        runBlocking {
            val result = apolloWrapper.performQuery(
                SearchQuery(query = "query"),
                userMustBeLoggedIn = true
            )
            expectThat(result.response).isNull()
        }

        coVerify(exactly = 1) { incognitoSessionToken.getOrFetchCookie() }
    }
}
