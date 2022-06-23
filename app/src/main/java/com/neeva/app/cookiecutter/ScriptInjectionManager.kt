package com.neeva.app.cookiecutter

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.neeva.app.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.chromium.weblayer.Tab
import org.chromium.weblayer.WebMessageCallback

class ScriptInjectionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: Dispatchers
) {
    private var engineScript: Deferred<String?> = coroutineScope.async(dispatchers.io) {
        loadEngineScript()
    }

    fun initializeMessagePassing(tab: Tab, callbacks: WebMessageCallback) {
        // calling this method once registers the __neeva_broker object for
        // all navigations in this tab
        tab.registerWebMessageCallback(callbacks, "__neeva_broker", listOf("*"))
    }

    fun unregisterMessagePassing(tab: Tab) {
        tab.unregisterWebMessageCallback("__neeva_broker")
    }

    fun injectNavigationCompletedScripts(tab: Tab, tabCookieCutterModel: TabCookieCutterModel) {
        // if our preferences say we shouldn't activate cookie cutter, then don't.
        if (!tabCookieCutterModel.shouldInjectCookieEngine) {
            return
        }

        coroutineScope.launch(dispatchers.main) {
            val scriptText = withContext(dispatchers.io) {
                engineScript.await()
            } ?: return@launch

            // Note: if you are expecting this script to run when the document has loaded,
            // that would be incorrect. It actually runs like halfway though. Make sure to attach
            // event handlers to, e.g. DOMContentLoaded, so that logic only runs when it should
            tab.executeScript(scriptText, false, null)
        }
    }

    @WorkerThread
    private fun loadEngineScript(): String? {
        return try {
            context.assets.open("cookieCutterEngine.js")
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Error while fetching cookie cutter engine script, not injecting.", e)
            null
        }
    }

    companion object {
        private val TAG = ScriptInjectionManager::class.simpleName
    }
}