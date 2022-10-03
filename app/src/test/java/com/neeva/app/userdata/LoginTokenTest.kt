// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.userdata

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neeva.app.BaseTest
import com.neeva.app.CoroutineScopeRule
import com.neeva.app.NeevaConstants
import com.neeva.app.sharedprefs.SharedPrefFolder
import com.neeva.app.sharedprefs.SharedPreferencesModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class LoginTokenTest : BaseTest() {
    @get:Rule
    val coroutineScopeRule = CoroutineScopeRule()

    @Test
    fun getToken_resultIsEmpty_returnsEmpty() {
        val sharedPreferencesModel = mock<SharedPreferencesModel> {
            on {
                getValue(any(), eq(SharedPrefFolder.User.Token.preferenceKey), eq(""), any())
            } doReturn ""
        }
        val neevaConstants = NeevaConstants()
        val loginToken = LoginToken(
            coroutineScope = coroutineScopeRule.scope,
            dispatchers = coroutineScopeRule.dispatchers,
            neevaConstants = neevaConstants,
            sharedPreferencesModel = sharedPreferencesModel
        )
        val result = loginToken.cookieValue
        expectThat(result).isEmpty()
    }

    @Test
    fun getToken_stringIsSet_returnsString() {
        val sharedPreferencesModel = mock<SharedPreferencesModel> {
            on {
                getValue(any(), eq(SharedPrefFolder.User.Token.preferenceKey), eq(""), any())
            } doReturn "whatever"
        }
        val neevaConstants = NeevaConstants()
        val loginToken = LoginToken(
            coroutineScope = coroutineScopeRule.scope,
            dispatchers = coroutineScopeRule.dispatchers,
            neevaConstants = neevaConstants,
            sharedPreferencesModel = sharedPreferencesModel
        )
        val result = loginToken.cookieValue
        expectThat(result).isEqualTo("whatever")
    }

    @Test
    fun extractAuthTokenFromIntent_givenValidString_getsItBack() {
        val intentUri = Uri.Builder()
            .scheme("neeva")
            .authority("login")
            .appendQueryParameter("somethingBefore", "whatever")
            .appendQueryParameter("sessionKey", "expectedSession")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, intentUri)
        val result = LoginToken.extractAuthTokenFromIntent(intent)
        expectThat(result).isEqualTo("expectedSession")
    }

    @Test
    fun extractAuthTokenFromIntent_withWrongScheme_returnsNull() {
        val intentUri = Uri.Builder()
            .scheme("https")
            .authority("login")
            .appendQueryParameter("somethingBefore", "whatever")
            .appendQueryParameter("sessionKey", "expectedSession")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, intentUri)
        val result = LoginToken.extractAuthTokenFromIntent(intent)
        expectThat(result).isNull()
    }

    @Test
    fun extractAuthTokenFromIntent_withWrongAuthority_returnsNull() {
        val intentUri = Uri.Builder()
            .scheme("neeva")
            .authority("wrongauthority.com")
            .appendQueryParameter("somethingBefore", "whatever")
            .appendQueryParameter("sessionKey", "expectedSession")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, intentUri)
        val result = LoginToken.extractAuthTokenFromIntent(intent)
        expectThat(result).isNull()
    }

    @Test
    fun updateCachedCookie() {
        val sharedPreferencesModel = mock<SharedPreferencesModel>()
        val neevaConstants = NeevaConstants()
        val loginToken = LoginToken(
            coroutineScope = coroutineScopeRule.scope,
            dispatchers = coroutineScopeRule.dispatchers,
            neevaConstants = neevaConstants,
            sharedPreferencesModel = sharedPreferencesModel
        )
        loginToken.updateCachedCookie("expectedToken")
        verify(sharedPreferencesModel).setValue(
            eq(SharedPrefFolder.User),
            eq(SharedPrefFolder.User.Token.preferenceKey),
            eq("expectedToken"),
            eq(true)
        )
    }

    @Test
    fun purgeCachedCookie() {
        val sharedPreferencesModel = mock<SharedPreferencesModel>()
        val neevaConstants = NeevaConstants()
        val loginToken = LoginToken(
            coroutineScope = coroutineScopeRule.scope,
            dispatchers = coroutineScopeRule.dispatchers,
            neevaConstants = neevaConstants,
            sharedPreferencesModel = sharedPreferencesModel
        )
        loginToken.purgeCachedCookie()
        verify(sharedPreferencesModel).removeValue(
            eq(SharedPrefFolder.User),
            eq(SharedPrefFolder.User.Token)
        )
    }
}