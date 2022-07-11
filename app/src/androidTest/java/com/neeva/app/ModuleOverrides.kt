package com.neeva.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.neeva.app.apollo.AuthenticatedApolloWrapper
import com.neeva.app.apollo.TestAuthenticatedApolloWrapper
import com.neeva.app.apollo.TestUnauthenticatedApolloWrapper
import com.neeva.app.apollo.UnauthenticatedApolloWrapper
import com.neeva.app.storage.HistoryDatabase
import com.neeva.app.userdata.NeevaUserToken
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NeevaConstantsModule::class]
)
object TestNeevaConstantsModule {
    /**
     * Sends the user to localhost instead of out to the real Neeva site.  Cookies are still set on
     * neeva.com to avoid WebLayer complaining about setting a secure cookie on an http site.
     */
    val neevaConstants = object : NeevaConstants(
        appHost = "127.0.0.1:8000",
        appURL = "http://127.0.0.1:8000/",
        cookieHost = "neeva.com",
        cookieURL = "https://neeva.com"
    ) {
        override val appHelpCenterURL = "http://127.0.0.1:8000/help.html"
    }

    @Provides
    @Singleton
    fun providesNeevaConstants(): NeevaConstants {
        return neevaConstants
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
        neevaUserToken: NeevaUserToken,
        neevaConstants: NeevaConstants
    ): AuthenticatedApolloWrapper {
        return TestAuthenticatedApolloWrapper(
            neevaUserToken = neevaUserToken,
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
