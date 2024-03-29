// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.browsing.urlbar

import android.content.Context
import android.net.Uri
import android.util.Patterns
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.graphics.drawable.toBitmap
import com.neeva.app.Dispatchers
import com.neeva.app.NeevaConstants
import com.neeva.app.R
import com.neeva.app.browsing.isNeevaSearchUri
import com.neeva.app.browsing.toSearchUri
import com.neeva.app.storage.favicons.FaviconCache
import com.neeva.app.suggestions.NavSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import org.chromium.weblayer.Browser
import org.chromium.weblayer.UrlBarController

class URLBarModelImpl(
    suggestionFlow: StateFlow<NavSuggestion?>,
    appContext: Context,
    coroutineScope: CoroutineScope,
    dispatchers: Dispatchers,
    private val faviconCache: FaviconCache,
    private val neevaConstants: NeevaConstants
) : URLBarModel {
    internal val neevaFavicon =
        AppCompatResources.getDrawable(appContext, R.drawable.ic_neeva_logo)?.toBitmap()

    private val _stateFlow = MutableStateFlow(URLBarModelState())
    override val stateFlow: StateFlow<URLBarModelState> get() = _stateFlow

    private val _urlBarControllerFlow = MutableStateFlow<UrlBarController?>(null)
    override val urlBarControllerFlow: StateFlow<UrlBarController?> get() = _urlBarControllerFlow

    init {
        // Update what is displayed in the URL bar as the user types.
        suggestionFlow
            .combine(_stateFlow) { suggestion, currentState ->
                val newState = determineDisplayState(suggestion, currentState)

                // Only update the data if nothing changed while we were processing the data.
                // This reduces the inherent raciness in doing things while the user is typing out
                // something in the URL bar.
                _stateFlow.compareAndSet(currentState, newState)
            }
            .flowOn(dispatchers.io)
            .launchIn(coroutineScope)
    }

    internal fun onBrowserChanged(browser: Browser?) {
        _urlBarControllerFlow.value = browser?.urlBarController
    }

    /** Determines what should be displayed in the URL bar as the user types something. */
    internal suspend fun determineDisplayState(
        suggestionValue: NavSuggestion?,
        userInputStateValue: URLBarModelState
    ): URLBarModelState {
        var newState = userInputStateValue.copy()
        val userInput = userInputStateValue.userTypedInput
        val suggestion = suggestionValue.takeIf {
            userInputStateValue.isAutocompleteAllowed || isExactMatch(suggestionValue, userInput)
        }

        // Check for an autocomplete match.
        val autocompletedText = computeAutocompleteText(suggestion, userInput)
            ?: run {
                // If there isn't a match, show the search provider's icon if the URI will perform
                // a search.
                val isSearchUri = userInputStateValue.uriToLoad.isNeevaSearchUri(neevaConstants)
                return newState.copy(
                    faviconBitmap = if (isSearchUri) neevaFavicon else null,
                    autocompleteSuggestion = null
                )
            }

        // Display the user's text with the autocomplete suggestion tacked on.
        newState = newState.copy(autocompleteSuggestion = autocompletedText)

        // Load the favicon from the cache, if it's available.
        val uriToLoad = suggestion?.url ?: getUrlToLoad(autocompletedText, neevaConstants)
        if (uriToLoad != userInputStateValue.uriToLoad) {
            val favicon = faviconCache.getFavicon(uriToLoad, false)
            newState = newState.copy(
                uriToLoad = uriToLoad,
                faviconBitmap = favicon
            )
        }

        return newState
    }

    override fun replaceLocationBarText(newValue: String, isRefining: Boolean) {
        showZeroQuery()

        val currentState = _stateFlow.value
        if (!currentState.isEditing) return

        _stateFlow.value = currentState.withUpdatedTextFieldValue(
            newTextFieldValue = TextFieldValue(
                text = newValue,
                selection = TextRange(newValue.length)
            ),

            // Disable autocomplete suggestions so that the user can keep refining queries that are
            // pasted directly into the URL bar from a query navigation suggestion.
            newIsAutocompleteAllowed = false,
            neevaConstants = neevaConstants
        )
            .copy(isRefining = isRefining)
    }

    /** Updates what is displayed in the URL bar as the user edits it. */
    override fun onLocationBarTextChanged(newValue: TextFieldValue) {
        val currentState = _stateFlow.value
        if (!currentState.isEditing) return

        val oldText = currentState.userTypedInput
        val didUserDeleteText =
            oldText.startsWith(newValue.text) && (oldText.length > newValue.text.length)
        val didUserAddText =
            newValue.text.startsWith(oldText) && oldText.length < newValue.text.length

        _stateFlow.value = when {
            didUserDeleteText -> {
                val isShowingSuggestion = !currentState.autocompleteSuggestionText.isNullOrEmpty()

                currentState.withUpdatedTextFieldValue(
                    newTextFieldValue = if (isShowingSuggestion) {
                        // If the user deleted a character and an autocomplete suggestion was being
                        // displayed, remove the suggestion and keep the user's old input.
                        currentState.textFieldValue
                    } else {
                        newValue
                    },

                    // The user deleted text from an existing string, so we should disable
                    // autocomplete suggestions to avoid trapping the user in a state where they are
                    // given the same suggestion over and over again.
                    newIsAutocompleteAllowed = false,
                    neevaConstants = neevaConstants
                )
            }

            didUserAddText -> {
                // The user appended more text to an existing string, so we should refresh the
                // autocomplete suggestions.
                currentState.withUpdatedTextFieldValue(
                    newTextFieldValue = newValue,
                    newIsAutocompleteAllowed = true,
                    neevaConstants = neevaConstants
                )
            }

            else -> {
                // The composition can change without changing the actual text string because the
                // keyboard service asynchronously determines what suggestions to show to the user.
                // When this happens, we should retain the previous autocompletion state.
                val onlyCompositionChanged =
                    currentState.textFieldValue.composition != newValue.composition &&
                        oldText == newValue.text
                val isAutocompleteAllowed =
                    currentState.isAutocompleteAllowed && onlyCompositionChanged

                currentState.withUpdatedTextFieldValue(
                    newTextFieldValue = newValue,
                    newIsAutocompleteAllowed = isAutocompleteAllowed,
                    neevaConstants = neevaConstants
                )
            }
        }
    }

    override fun acceptAutocompleteSuggestion() {
        _stateFlow.value.autocompleteSuggestion?.let { replaceLocationBarText(it) }
    }

    override fun showZeroQuery(focusUrlBar: Boolean, isLazyTab: Boolean) {
        if (_stateFlow.value.isEditing) return

        _stateFlow.value = URLBarModelState(
            isEditing = true,
            isLazyTab = isLazyTab,
            focusUrlBar = focusUrlBar
        )
    }

    override fun clearFocus() {
        _stateFlow.value = URLBarModelState(
            isEditing = false,
            isLazyTab = false
        )
    }

    companion object {
        internal fun matchesLocalhost(urlBarContents: String): Boolean {
            return Regex("(http:(//)?)?localhost(:\\d+)?(/(.*)?)?").matches(urlBarContents)
        }

        /**
         * Determines if what is in the URL bar is related to the [autocompletedSuggestion] via the
         * given [comparator].
         *
         * This applies a bunch of hand wavy heuristics, including chopping off "https://www" and
         * checking if there's a straight match.
         */
        private fun fuzzyMatchSuggestion(
            autocompletedSuggestion: NavSuggestion?,
            urlBarContents: String?,
            comparator: (String, String) -> Boolean
        ): String? {
            if (urlBarContents.isNullOrBlank() || autocompletedSuggestion == null) return null

            // Perform fuzzy matching before checking for a direct match because it's unlikely that
            // a user would type in "https://www." before anything else.
            val suggestionUri = autocompletedSuggestion.url.toString().takeIf { it.isNotBlank() }
                ?: return null
            listOf("https://www.", "https://", "http://www.", "http://").forEach { prefix ->
                suggestionUri.takeIf { it.startsWith(prefix) }
                    ?.removePrefix(prefix)
                    ?.let { if (comparator(it, urlBarContents)) return it }
            }

            // Check if we have a direct match.
            if (comparator(suggestionUri, urlBarContents)) return suggestionUri

            // There's no way we can get a match on the URL.
            return null
        }

        internal fun computeAutocompleteText(
            autocompletedSuggestion: NavSuggestion?,
            urlBarContents: String?,
        ): String? {
            return fuzzyMatchSuggestion(autocompletedSuggestion, urlBarContents) { first, second ->
                first.startsWith(second)
            }
        }

        private fun isExactMatch(
            autocompletedSuggestion: NavSuggestion?,
            urlBarContents: String?
        ): Boolean {
            return fuzzyMatchSuggestion(autocompletedSuggestion, urlBarContents) { first, second ->
                first == second
            } != null
        }

        /** Returns which URL should be loaded when the user submits their text. */
        internal fun getUrlToLoad(urlBarContents: String, neevaConstants: NeevaConstants): Uri {
            return when {
                // Try to figure out if the user typed in a query or a URL.
                Patterns.WEB_URL.matcher(urlBarContents).matches() -> {
                    Uri.parse(
                        when {
                            !urlBarContents.startsWith("http") -> "http://$urlBarContents"
                            else -> urlBarContents
                        }
                    )
                }

                matchesLocalhost(urlBarContents) -> Uri.parse(urlBarContents)

                else -> urlBarContents.toSearchUri(neevaConstants)
            }
        }

        /** Creates a new [URLBarModelState] accounting for a change in the text. */
        internal fun URLBarModelState.withUpdatedTextFieldValue(
            newTextFieldValue: TextFieldValue,
            newIsAutocompleteAllowed: Boolean,
            neevaConstants: NeevaConstants
        ): URLBarModelState {
            var newState = copy(
                isAutocompleteAllowed = newIsAutocompleteAllowed,
                textFieldValue = newTextFieldValue
            )

            val isAutocompleteStillValid =
                autocompleteSuggestion?.startsWith(newTextFieldValue.text) == true
            if (!newIsAutocompleteAllowed || !isAutocompleteStillValid) {
                // Toss out the existing autocomplete suggestion.
                newState = newState.copy(
                    autocompleteSuggestion = null,
                    uriToLoad = getUrlToLoad(newState.textFieldValue.text, neevaConstants),
                    faviconBitmap = null
                )
            }

            return newState
        }
    }
}
