package com.neeva.app.browsing

import android.net.Uri
import org.chromium.weblayer.NavigateParams
import org.chromium.weblayer.Tab

/** Returns the [Tab]'s [Browser] if it hasn't been destroyed, and null otherwise. */
fun Tab.getBrowserIfAlive() = browser.takeUnless { it.isDestroyed }

val Tab.currentDisplayUrl: Uri?
    get() {
        navigationController.apply {
            return if (navigationListSize == 0) {
                null
            } else {
                getNavigationEntryDisplayUri(navigationListCurrentIndex)
            }
        }
    }

val Tab.currentDisplayTitle: String?
    get() {
        navigationController.apply {
            return if (navigationListSize == 0) {
                null
            } else {
                getNavigationEntryTitle(navigationListCurrentIndex)
            }
        }
    }

val Tab.isSelected: Boolean
    get() {
        return getBrowserIfAlive()?.activeTab == this
    }

/** Navigates the Tab to the given URL. */
fun Tab.navigate(uri: Uri, stayInApp: Boolean) {
    val navigateParams = NavigateParams.Builder()
        .apply {
            if (stayInApp && (uri.scheme == "https" || uri.scheme == "http")) {
                // Disable intent processing for websites that would send the browser to other apps.
                disableIntentProcessing()
            }
        }
        .build()

    navigationController.navigate(uri, navigateParams)
}
