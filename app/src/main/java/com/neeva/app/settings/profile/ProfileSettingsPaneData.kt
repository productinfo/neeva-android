package com.neeva.app.settings.profile

import com.neeva.app.R
import com.neeva.app.settings.SettingsGroupData
import com.neeva.app.settings.SettingsRowData
import com.neeva.app.settings.SettingsRowType

object ProfileSettingsPaneData {
    val data = listOf(
        SettingsGroupData(
            R.string.settings_signed_into_neeva_with,
            listOf(
                SettingsRowData(
                    type = SettingsRowType.PROFILE,
                    titleId = R.string.settings_sign_in_to_join_neeva,
                    showSSOProviderAsPrimaryLabel = true
                ),
                SettingsRowData(
                    type = SettingsRowType.BUTTON,
                    titleId = R.string.settings_sign_out
                )
            )
        )
    )
}
