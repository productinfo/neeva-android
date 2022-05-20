package com.neeva.app.settings

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.neeva.app.cookiecutter.CookieCutterModel
import com.neeva.app.settings.clearBrowsing.TimeClearingOption
import com.neeva.app.settings.clearBrowsing.TimeClearingOptionsConstants
import com.neeva.app.sharedprefs.SharedPrefFolder
import com.neeva.app.sharedprefs.SharedPreferencesModel

/**
 * A data model for getting any Settings-related state ([SettingsToggle] or [TimeClearingOption]).
 * Used to get toggle states in SettingsController.
 * FEATURE FLAGGING: used in any @Composable or anywhere else to get if a Feature Flag is enabled.
 *
 * This includes:
 *    - Holding all toggle MutableStates (which are based on their SharedPref values)
 *    - Being a wrapper class for Settings-SharedPreferences
 *    - Holding DEBUG-mode-only flags as MutableStates
 */
class SettingsDataModel(val sharedPreferencesModel: SharedPreferencesModel) {
    private val toggleMap = mutableMapOf<String, MutableState<Boolean>>()
    private val cookieCutterMode = mutableStateOf(
        CookieCutterModel.BlockingStrength.valueOf(
            getSharedPrefValue(
                CookieCutterModel.BLOCKING_STRENGTH_SHARED_PREF_KEY,
                CookieCutterModel.BlockingStrength.TRACKER_COOKIE.name
            )
        )
    )
    private val selectedTimeClearingOptionIndex = mutableStateOf(
        getSharedPrefValue(TimeClearingOptionsConstants.sharedPrefKey, 0)
    )

    init {
        SettingsToggle.values().forEach {
            toggleMap[it.key] = mutableStateOf(getSharedPrefValue(it.key, it.defaultValue))
        }
    }

    private fun <T> getSharedPrefValue(key: String, defaultValue: T): T {
        return sharedPreferencesModel.getValue(SharedPrefFolder.SETTINGS, key, defaultValue)
    }

    private fun setSharedPrefValue(key: String, newValue: Any) {
        sharedPreferencesModel.setValue(SharedPrefFolder.SETTINGS, key, newValue)
    }

    fun getTogglePreferenceSetter(settingsToggle: SettingsToggle): (Boolean) -> Unit {
        return { newToggleValue ->
            getToggleState(settingsToggle).value = newToggleValue
            setSharedPrefValue(settingsToggle.key, newToggleValue)
        }
    }

    fun getSettingsToggleValue(settingsToggle: SettingsToggle): Boolean {
        return getToggleState(settingsToggle).value
    }

    fun getToggleState(settingsToggle: SettingsToggle): MutableState<Boolean> {
        check(toggleMap[settingsToggle.key] != null)
        return toggleMap[settingsToggle.key] ?: mutableStateOf(false)
    }

    fun getCookieCutterStrength(): CookieCutterModel.BlockingStrength {
        return cookieCutterMode.value
    }

    fun setCookieCutterStrength(strength: CookieCutterModel.BlockingStrength) {
        setSharedPrefValue(CookieCutterModel.BLOCKING_STRENGTH_SHARED_PREF_KEY, strength.name)
        cookieCutterMode.value = strength
    }

    fun getTimeClearingOptionIndex(): MutableState<Int> {
        return selectedTimeClearingOptionIndex
    }

    fun saveSelectedTimeClearingOption(index: Int) {
        setSharedPrefValue(TimeClearingOptionsConstants.sharedPrefKey, index)
    }

    fun toggleIsAdvancedSettingsAllowed() {
        val newValue = !getSettingsToggleValue(SettingsToggle.IS_ADVANCED_SETTINGS_ALLOWED)
        getTogglePreferenceSetter(SettingsToggle.IS_ADVANCED_SETTINGS_ALLOWED).invoke(newValue)
    }
}
