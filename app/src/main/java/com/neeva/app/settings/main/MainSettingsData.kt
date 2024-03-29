// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.settings.main

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import com.neeva.app.LocalSharedPreferencesModel
import com.neeva.app.NeevaConstants
import com.neeva.app.R
import com.neeva.app.settings.SettingsGroupData
import com.neeva.app.settings.SettingsPaneDataInterface
import com.neeva.app.settings.SettingsRowData
import com.neeva.app.settings.SettingsRowType
import com.neeva.app.settings.SettingsToggle
import com.neeva.app.sharedprefs.SharedPrefFolder

class MainSettingsData(neevaConstants: NeevaConstants) : SettingsPaneDataInterface {
    @StringRes
    override val topAppBarTitleResId: Int = R.string.settings
    override val shouldShowUserName: Boolean = false
    override val data = listOf(
        SettingsGroupData(
            R.string.settings_account,
            listOf(
                SettingsRowData(
                    type = SettingsRowType.PROFILE,
                    primaryLabelId = R.string.settings_sign_in_to_join_neeva
                ),
                SettingsRowData(
                    type = SettingsRowType.LINK,
                    primaryLabelId = R.string.settings_account_settings,
                    url = Uri.parse(neevaConstants.appSettingsURL)
                ),
                SettingsRowData(
                    type = SettingsRowType.LINK,
                    primaryLabelId = R.string.settings_connected_apps,
                    url = Uri.parse(neevaConstants.appConnectionsURL)
                ),
                SettingsRowData(
                    type = SettingsRowType.LINK,
                    primaryLabelId = R.string.settings_invite_friends,
                    url = Uri.parse(neevaConstants.appReferralURL)
                ),
            )
        ),
        SettingsGroupData(
            R.string.settings_general,
            listOf(
                SettingsRowData(
                    type = SettingsRowType.NAVIGATION,
                    primaryLabelId = R.string.settings_default_browser
                ),
                SettingsRowData(
                    type = SettingsRowType.TOGGLE,
                    settingsToggle = SettingsToggle.SHOW_SEARCH_SUGGESTIONS
                ),
                SettingsRowData(
                    type = SettingsRowType.TOGGLE,
                    settingsToggle = SettingsToggle.REQUIRE_CONFIRMATION_ON_TAB_CLOSE,
                ),
                SettingsRowData(
                    type = SettingsRowType.NAVIGATION,
                    primaryLabelId = R.string.archived_tabs_archive,
                    secondaryLabelLambda = @Composable {
                        stringResource(
                            SharedPrefFolder.App.AutomaticallyArchiveTabs
                                .getFlow(LocalSharedPreferencesModel.current)
                                .collectAsState()
                                .value
                                .resourceId
                        )
                    }
                )
            )
        ),
        SettingsGroupData(
            R.string.settings_privacy,
            listOf(
                SettingsRowData(
                    type = SettingsRowType.NAVIGATION,
                    primaryLabelId = R.string.settings_clear_browsing_data
                ),
                SettingsRowData(
                    type = SettingsRowType.TOGGLE,
                    settingsToggle = SettingsToggle.CLOSE_INCOGNITO_TABS
                ),
                SettingsRowData(
                    type = SettingsRowType.NAVIGATION,
                    primaryLabelId = R.string.content_filter
                ),
                SettingsRowData(
                    type = SettingsRowType.LINK,
                    primaryLabelId = R.string.settings_privacy_policy,
                    url = Uri.parse(neevaConstants.appPrivacyURL)
                ),
                SettingsRowData(
                    type = SettingsRowType.TOGGLE,
                    settingsToggle = SettingsToggle.LOGGING_CONSENT
                )
            )
        ),
        SettingsGroupData(
            R.string.settings_about,
            listOf(
                SettingsRowData(
                    type = SettingsRowType.BUTTON,
                    primaryLabelId = R.string.settings_neeva_browser_version,
                    secondaryLabelId = R.string.settings_chromium_version,
                    openUrlViaIntent = true
                ),
                SettingsRowData(
                    type = SettingsRowType.LINK,
                    primaryLabelId = R.string.settings_help_center,
                    url = Uri.parse(neevaConstants.appHelpCenterURL)
                ),
                SettingsRowData(
                    type = SettingsRowType.LINK,
                    primaryLabelId = R.string.settings_play_store_page,
                    url = neevaConstants.playStoreUri,
                    openUrlViaIntent = true
                ),
                SettingsRowData(
                    type = SettingsRowType.NAVIGATION,
                    primaryLabelId = R.string.settings_licenses
                ),
                SettingsRowData(
                    type = SettingsRowType.LINK,
                    primaryLabelId = R.string.settings_terms,
                    url = Uri.parse(neevaConstants.appTermsURL)
                ),
            )
        ),
        SettingsGroupData(
            R.string.settings_debug_local,
            listOf(
                SettingsRowData(
                    type = SettingsRowType.NAVIGATION,
                    primaryLabelId = R.string.settings_debug_local_feature_flags
                )
            ),
            isForDebugOnly = true
        )
    )
}
