package com.neeva.app.sharedprefs

import com.neeva.app.browsing.ArchiveAfterOption
import com.neeva.app.ui.widgets.collapsingsection.CollapsingSectionState
import com.neeva.app.ui.widgets.collapsingsection.CollapsingSectionState.Companion.toCollapsingSectionState

/**
 * Groups the SharedPreferences of related features.
 *
 * NEVER change the [folderName] or we will lose access to the previously saved values.
 */
sealed class SharedPrefFolder(internal val folderName: String) {
    object App : SharedPrefFolder("APP") {
        val AutomaticallyArchiveTabs = SharedPrefKey(
            App,
            preferenceKey = "AUTOMATICALLY_ARCHIVE_TABS",
            defaultValue = ArchiveAfterOption.AFTER_7_DAYS,
            enumFromString = ArchiveAfterOption::fromString
        )

        val CheckForImportedDatabaseKey = SharedPrefKey(
            App,
            preferenceKey = "CHECK_FOR_IMPORTED_DATABASE_KEY",
            defaultValue = false
        )

        val CustomNeevaDomain = SharedPrefKey(
            App,
            preferenceKey = "CUSTOM_NEEVA_DOMAIN",
            defaultValue = "m1.neeva.com"
        )

        val SessionIdV2Key = SharedPrefKey(
            App,
            preferenceKey = "SESSION_ID_V2",
            defaultValue = ""
        )

        /**
         * Tracks whether the user is using the Regular or Incognito profile.  Meant to be read only
         * during WebLayerModel initialization.
         */
        val IsCurrentlyIncognito = SharedPrefKey(
            App,
            preferenceKey = "IS_CURRENTLY_INCOGNITO",
            defaultValue = false
        )

        val SpacesShowDescriptionsPreferenceKey = SharedPrefKey(
            App,
            preferenceKey = "SPACES_SHOW_DESCRIPTIONS",
            defaultValue = false
        )

        val ZeroQuerySuggestedSitesState = SharedPrefKey(
            App,
            preferenceKey = "ZERO_QUERY_SUGGESTED_SITES_STATE",
            defaultValue = CollapsingSectionState.COMPACT,
            enumFromString = { it.toCollapsingSectionState() }
        )
        val ZeroQuerySuggestedQueriesState = SharedPrefKey(
            App,
            preferenceKey = "ZERO_QUERY_SUGGESTED_QUERIES_STATE",
            defaultValue = CollapsingSectionState.EXPANDED,
            enumFromString = { it.toCollapsingSectionState() }
        )
        val ZeroQueryCommunitySpacesState = SharedPrefKey(
            App,
            preferenceKey = "ZERO_QUERY_COMMUNITY_SPACES_STATE",
            defaultValue = CollapsingSectionState.EXPANDED,
            enumFromString = { it.toCollapsingSectionState() }
        )
        val ZeroQuerySpacesState = SharedPrefKey(
            App,
            preferenceKey = "ZERO_QUERY_SPACES_STATE",
            defaultValue = CollapsingSectionState.EXPANDED,
            enumFromString = { it.toCollapsingSectionState() }
        )
    }

    object FirstRun : SharedPrefFolder("FIRST_RUN") {
        val FirstRunDone = SharedPrefKey(
            FirstRun,
            preferenceKey = "HAS_FINISHED_FIRST_RUN",
            defaultValue = false
        )
        val HasSignedInBefore = SharedPrefKey(
            FirstRun,
            preferenceKey = "HAS_SIGNED_IN_AT_LEAST_ONCE",
            defaultValue = false
        )
        val PreviewQueryCount = SharedPrefKey(
            FirstRun,
            preferenceKey = "NUM_PREVIEW_QUERIES",
            defaultValue = 0
        )
        val ShouldLogFirstLogin = SharedPrefKey(
            FirstRun,
            preferenceKey = "SHOULD_LOG_FIRST_LOGIN",
            defaultValue = false
        )
    }

    object Settings : SharedPrefFolder("SETTINGS")

    object User : SharedPrefFolder("USER") {
        val Token = SharedPrefKey(
            User,
            preferenceKey = "TOKEN",
            defaultValue = ""
        )
    }
}