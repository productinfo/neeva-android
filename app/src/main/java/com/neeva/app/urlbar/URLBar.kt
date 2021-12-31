package com.neeva.app.urlbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.neeva.app.history.DomainViewModel
import com.neeva.app.suggestions.SuggestionsViewModel

@Composable
fun URLBar(
    suggestionsViewModel: SuggestionsViewModel,
    urlBarModel: URLBarModel,
    domainViewModel: DomainViewModel
) {
    val isEditing: Boolean by urlBarModel.isEditing.collectAsState()
    val showLock: Boolean by urlBarModel.showLock.collectAsState()
    val value: TextFieldValue by urlBarModel.text.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.primary)
            .padding(horizontal = 8.dp)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colors.primaryVariant)
                .height(40.dp)
                .padding(horizontal = 8.dp)
        ) {
            AutocompleteTextField(suggestionsViewModel, urlBarModel, domainViewModel::getFaviconFlow)

            // We need to have both the AutocompleteTextField and the LocationLabel in the URLBar
            // at the same time because the AutocompleteTextField is the thing that must be focused
            // when the LocationLabel is clicked.
            if (!isEditing) {
                LocationLabel(
                    urlBarValue = value.text,
                    showLock = showLock,
                    onReload = urlBarModel::onReload,
                    modifier = Modifier.clickable { urlBarModel.onRequestFocus() }
                )
            }
        }
    }
}