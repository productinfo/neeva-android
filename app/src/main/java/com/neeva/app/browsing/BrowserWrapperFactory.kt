package com.neeva.app.browsing

import android.app.Application
import com.neeva.app.Dispatchers
import com.neeva.app.NeevaConstants
import com.neeva.app.apollo.AuthenticatedApolloWrapper
import com.neeva.app.apollo.UnauthenticatedApolloWrapper
import com.neeva.app.cookiecutter.ScriptInjectionManager
import com.neeva.app.history.HistoryManager
import com.neeva.app.logging.ClientLogger
import com.neeva.app.publicsuffixlist.DomainProvider
import com.neeva.app.settings.SettingsDataModel
import com.neeva.app.spaces.SpaceStore
import com.neeva.app.storage.Directories
import com.neeva.app.storage.HistoryDatabase
import com.neeva.app.storage.favicons.RegularFaviconCache
import com.neeva.app.ui.PopupModel
import com.neeva.app.userdata.NeevaUser
import kotlinx.coroutines.CoroutineScope

class BrowserWrapperFactory(
    private val activityCallbackProvider: ActivityCallbackProvider,
    private val application: Application,
    private val authenticatedApolloWrapper: AuthenticatedApolloWrapper,
    private val unauthenticatedApolloWrapper: UnauthenticatedApolloWrapper,
    private val clientLogger: ClientLogger,
    private val directories: Directories,
    private val dispatchers: Dispatchers,
    private val domainProvider: DomainProvider,
    private val historyManager: HistoryManager,
    private val historyDatabase: HistoryDatabase,
    private val neevaConstants: NeevaConstants,
    private val neevaUser: NeevaUser,
    private val regularFaviconCache: RegularFaviconCache,
    private val scriptInjectionManager: ScriptInjectionManager,
    private val settingsDataModel: SettingsDataModel,
    private val spaceStore: SpaceStore,
    private val popupModel: PopupModel
) {
    fun createRegularBrowser(coroutineScope: CoroutineScope): RegularBrowserWrapper {
        return RegularBrowserWrapper(
            appContext = application,
            activityCallbackProvider = activityCallbackProvider,
            authenticatedApolloWrapper = authenticatedApolloWrapper,
            clientLogger = clientLogger,
            coroutineScope = coroutineScope,
            directories = directories,
            dispatchers = dispatchers,
            domainProvider = domainProvider,
            historyManager = historyManager,
            hostInfoDao = historyDatabase.hostInfoDao(),
            searchNavigationDao = historyDatabase.searchNavigationDao(),
            neevaConstants = neevaConstants,
            neevaUser = neevaUser,
            regularFaviconCache = regularFaviconCache,
            scriptInjectionManager = scriptInjectionManager,
            settingsDataModel = settingsDataModel,
            spaceStore = spaceStore,
            popupModel = popupModel
        )
    }

    fun createIncognitoBrowser(
        coroutineScope: CoroutineScope,
        onRemovedFromHierarchy: (incognitoBrowserWrapper: IncognitoBrowserWrapper) -> Unit
    ): IncognitoBrowserWrapper {
        return IncognitoBrowserWrapper(
            appContext = application,
            coroutineScope = coroutineScope,
            directories = directories,
            dispatchers = dispatchers,
            activityCallbackProvider = activityCallbackProvider,
            authenticatedApolloWrapper = authenticatedApolloWrapper,
            unauthenticatedApolloWrapper = unauthenticatedApolloWrapper,
            domainProvider = domainProvider,
            onRemovedFromHierarchy = onRemovedFromHierarchy,
            neevaConstants = neevaConstants,
            scriptInjectionManager = scriptInjectionManager,
            settingsDataModel = settingsDataModel,
            popupModel = popupModel,
            neevaUser = neevaUser
        )
    }
}
