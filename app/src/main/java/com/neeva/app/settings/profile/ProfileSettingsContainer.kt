package com.neeva.app.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import com.neeva.app.LocalEnvironment
import com.neeva.app.browsing.WebLayerModel
import com.neeva.app.settings.profile.ProfileSettingsPane

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ProfileSettingsContainer(
    webLayerModel: WebLayerModel,
    onBackPressed: () -> Unit
) {
    val neevaUser = LocalEnvironment.current.neevaUser
    ProfileSettingsPane(
        onBackPressed = onBackPressed,
        signUserOut = {
            neevaUser.signOut(webLayerModel)
            onBackPressed()
        },
        neevaUserData = neevaUser.data
    )
}