// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.neeva.app.firstrun.FirstRunModel
import com.neeva.app.settings.SettingsDataModel
import com.neeva.app.settings.SettingsToggle
import com.neeva.app.sharedprefs.SharedPrefFolder
import com.neeva.app.sharedprefs.SharedPreferencesModel
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Forces the Neeva app to skip First Run or NeevaScope tooltip when it starts. */
class PresetSharedPreferencesRule(
    val skipFirstRun: Boolean = true,
    val skipNeevaScopeTooltip: Boolean = true,
    val useCustomTabsForLogin: Boolean? = null
) : TestRule {
    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Application>()
                val sharedPreferencesModel = SharedPreferencesModel(context)
                val settingsDataModel = SettingsDataModel(sharedPreferencesModel)

                if (skipFirstRun) {
                    FirstRunModel.setFirstRunDone(sharedPreferencesModel)
                }

                if (skipNeevaScopeTooltip) {
                    SharedPrefFolder.App.NeevaScopeTooltipCount.set(
                        sharedPreferencesModel,
                        0
                    )
                }

                useCustomTabsForLogin?.let {
                    settingsDataModel.setToggleState(
                        settingsToggle = SettingsToggle.DEBUG_USE_CUSTOM_TABS_FOR_LOGIN,
                        newToggleValue = it
                    )
                }

                base?.evaluate()
            }
        }
    }
}
