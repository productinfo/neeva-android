// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.neeva.app.apollo.AuthenticatedApolloWrapper
import com.neeva.app.apollo.UnauthenticatedApolloWrapper
import com.neeva.app.firstrun.OktaSignUpHandler
import com.neeva.app.storage.HistoryDatabase
import com.neeva.app.userdata.LoginToken
import com.neeva.app.userdata.PreviewSessionToken
import com.neeva.testcommon.apollo.TestAuthenticatedApolloWrapper
import com.neeva.testcommon.apollo.TestUnauthenticatedApolloWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NeevaConstantsModule::class]
)
object TestNeevaConstantsModule {
    @Provides
    @Singleton
    fun providesNeevaConstants(@ApplicationContext context: Context): NeevaConstants {
        /**
         * Sends the user to localhost instead of out to the real Neeva site.  Cookies are still set on
         * neeva.com to avoid WebLayer complaining about setting a secure cookie on an http site.
         */
        return object : NeevaConstants(
            appHost = "127.0.0.1:8000",
            appURL = "http://127.0.0.1:8000/"
        ) {
            override val downloadDirectory = File(context.cacheDir, "/testDownloads")

            override val appHelpCenterURL = "http://127.0.0.1:8000/help.html"

            // No local equivalent for cookie cutter url, but this should suffice for testing.
            override val contentFilterLearnMoreUrl = appHelpCenterURL

            override fun createPersistentNeevaCookieString(
                cookieName: String,
                cookieValue: String,
                isSessionToken: Boolean,
                durationMinutes: Int
            ): String {
                // Creates a cookie string that doesn't explicitly define the Domain, which OkHttp
                // requires for its Cookie builder.
                val durationSeconds = TimeUnit.MINUTES.toSeconds(durationMinutes.toLong())
                return StringBuilder()
                    .append("$cookieName=$cookieValue")
                    .append("; max-age=$durationSeconds")
                    .append("; secure")
                    .apply {
                        if (isSessionToken) append("; HttpOnly")
                    }
                    .toString()
            }
        }
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ApolloModule::class]
)
class TestApolloModule {
    @Provides
    @Singleton
    fun providesAuthenticatedApolloWrapper(
        loginToken: LoginToken,
        previewSessionToken: PreviewSessionToken,
        neevaConstants: NeevaConstants
    ): AuthenticatedApolloWrapper {
        return TestAuthenticatedApolloWrapper(
            loginToken = loginToken,
            previewSessionToken = previewSessionToken,
            neevaConstants = neevaConstants
        )
    }

    @Provides
    @Singleton
    fun providesUnauthenticatedApolloWrapper(
        neevaConstants: NeevaConstants
    ): UnauthenticatedApolloWrapper {
        return TestUnauthenticatedApolloWrapper(neevaConstants = neevaConstants)
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
class TestDatabaseModule {
    @Provides
    @Singleton
    fun providesHistoryDatabase(): HistoryDatabase {
        val context: Context = ApplicationProvider.getApplicationContext()
        return HistoryDatabase.createInMemory(context)
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [OktaModule::class]
)
class TestOktaModule {
    class TestOktaSignUpHandler(
        neevaConstants: NeevaConstants,
    ) : OktaSignUpHandler(neevaConstants) {
        var overrideCreateOktaAccountUrl: String? = null
        var overrideOnLoginCookieReceivedUrl: String? = null

        override val createOktaAccountURL: String
            get() = overrideCreateOktaAccountUrl ?: super.createOktaAccountURL

        override val onLoginCookieReceivedUrl: String
            get() = overrideOnLoginCookieReceivedUrl ?: super.onLoginCookieReceivedUrl
    }

    @Provides
    @Singleton
    fun providesOktaSignUpHandler(neevaConstants: NeevaConstants): OktaSignUpHandler {
        return TestOktaSignUpHandler(neevaConstants)
    }
}
