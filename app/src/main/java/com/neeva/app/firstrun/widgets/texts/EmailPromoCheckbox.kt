// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.firstrun.widgets.texts

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.neeva.app.R
import com.neeva.app.firstrun.FirstRunConstants
import com.neeva.app.ui.LightDarkPreviewContainer
import com.neeva.app.ui.theme.Dimensions

// TODO(yusuf/kobe): make the checkbox actually send product & privacy tips
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailPromoCheckbox(modifier: Modifier = Modifier) {
    var checked by remember { mutableStateOf(true) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { checked = !checked }
            )
            .fillMaxWidth()
            .wrapContentSize(align = Alignment.Center)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(Dimensions.PADDING_LARGE))
        Text(
            text = stringResource(R.string.send_me_product_and_privacy_tips),
            style = FirstRunConstants.getSubtextStyle()
        )
    }
}

@Preview("EmailPromoCheckbox LTR 1x scale", locale = "en")
@Preview("EmailPromoCheckbox LTR 2x scale", locale = "en", fontScale = 2.0f)
@Preview("EmailPromoCheckbox RTL 1x scale", locale = "he")
@Composable
fun EmailPromoCheckboxPreview() {
    LightDarkPreviewContainer {
        EmailPromoCheckbox()
    }
}
