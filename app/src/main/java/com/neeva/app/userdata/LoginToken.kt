// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.userdata

import com.neeva.app.Dispatchers
import com.neeva.app.NeevaConstants
import com.neeva.app.sharedprefs.SharedPrefFolder
import com.neeva.app.sharedprefs.SharedPreferencesModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Response
import org.chromium.weblayer.Browser

/** Manages the login cookie that tracks whether the user is signed in or not. */
class LoginToken(
    coroutineScope: CoroutineScope,
    dispatchers: Dispatchers,
    neevaConstants: NeevaConstants,
    private val sharedPreferencesModel: SharedPreferencesModel,
) : SessionToken(
    coroutineScope = coroutineScope,
    dispatchers = dispatchers,
    neevaConstants = neevaConstants,
    endpointURL = null,
    cookieName = neevaConstants.loginCookieKey
) {
    override val cachedValue: String
        get() = SharedPrefFolder.User.Token.get(sharedPreferencesModel)
    val cachedValueFlow: StateFlow<String>
        get() = SharedPrefFolder.User.Token.getFlow(sharedPreferencesModel)

    /**
     * Returns whether or not we have a login cookie cached.
     *
     * If this function returns true, then the user is logged out.
     */
    fun isEmpty(): Boolean = cachedValue.isEmpty()
    fun isNotEmpty(): Boolean = !isEmpty()

    /**
     * Retrieves the cookie value directly from the WebLayer [Browser] -- if one is available.
     *
     * Because user input is required to get a login cookie, we make no attempt to fire a network
     * request to fetch a login cookie if no cookie is set.
     */
    override suspend fun getOrFetchCookie(): String? {
        return weakBrowser.getCookieFromBrowser().second?.value
    }

    override fun updateCachedCookie(newValue: String) {
        if (cachedValue == newValue) return

        if (newValue.isEmpty()) {
            SharedPrefFolder.User.Token.remove(
                sharedPreferencesModel = sharedPreferencesModel
            )
        } else if (cachedValue != newValue) {
            SharedPrefFolder.User.Token.set(
                sharedPreferencesModel = sharedPreferencesModel,
                value = newValue,
                mustCommitImmediately = true
            )
        }
    }

    override fun purgeCachedCookie(callback: (success: Boolean) -> Unit) {
        updateCookieManager("") { success ->
            if (success) {
                updateCachedCookie("")
            }
            callback(success)
        }
    }

    /** No-op: The user has to provide login credentials via the sign-in flow. */
    override fun requestNewCookie() {}
    override suspend fun processResponse(response: Response): Boolean = false
}
