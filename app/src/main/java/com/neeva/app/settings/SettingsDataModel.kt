package com.neeva.app.settings

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.neeva.app.sharedprefs.SharedPrefFolder
import com.neeva.app.sharedprefs.SharedPreferencesModel
import com.neeva.app.userdata.NeevaUser

/**
 * A data model for Settings composables.
 *
 * This includes:
 *    - Holding all toggle MutableStates (which are based on their SharedPref values)
 *    - Checking if the user is signed out to display ProfileRow or not
 *    - Storing and uses Settings-SharedPreferences
 */
class SettingsDataModel(
    val sharedPreferencesModel: SharedPreferencesModel
) {
    private val toggleMap = mutableMapOf<String, MutableState<Boolean>>()

    init {
        SettingsToggle.values().forEach {
            toggleMap[it.key] = mutableStateOf(getSharedPrefValue(it.key, it.defaultValue))
        }
    }

    private fun getSharedPrefValue(key: String, default: Boolean): Boolean {
        return sharedPreferencesModel.getBoolean(SharedPrefFolder.SETTINGS, key, default)
    }

    private fun setSharedPrefValue(key: String, newValue: Boolean) {
        sharedPreferencesModel.setValue(SharedPrefFolder.SETTINGS, key, newValue)
    }

    fun getTogglePreferenceSetter(togglePreferenceKey: String?): ((Boolean) -> Unit)? {
        return { newValue ->
            val toggleState = toggleMap[togglePreferenceKey]
            toggleState?.value = newValue
            togglePreferenceKey?.let { setSharedPrefValue(it, newValue) }
        }
    }

    fun getToggleState(toggleKeyName: String?): MutableState<Boolean>? {
        return toggleKeyName?.let { toggleMap[toggleKeyName] }
    }

    fun isSignedOut(neevaUser: NeevaUser): Boolean {
        return neevaUser.isSignedOut()
    }
}
