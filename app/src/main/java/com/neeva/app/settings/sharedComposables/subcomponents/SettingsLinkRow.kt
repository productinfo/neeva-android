package com.neeva.app.settings.sharedComposables.subcomponents

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neeva.app.R
import com.neeva.app.ui.theme.NeevaTheme

@Composable
fun SettingsLinkRow(
    title: String,
    openUrl: (Uri) -> Unit,
    uri: Uri,
    modifier: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable { openUrl(uri) }
    ) {
        SettingsLabelText(text = title, modifier = Modifier.weight(1.0f))
        Image(
            painter = painterResource(R.drawable.ic_baseline_open_in_new_24),
            contentDescription = title,
            contentScale = ContentScale.Inside,
            modifier = Modifier.size(48.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Preview(name = "Settings Link Row, 1x font size", locale = "en")
@Preview(name = "Settings Link Row, 2x font size", locale = "en", fontScale = 2.0f)
@Preview(name = "Settings Link Row, RTL, 1x font size", locale = "he")
@Preview(name = "Settings Link Row, RTL, 2x font size", locale = "he", fontScale = 2.0f)
@Composable
fun SettingsLinkRow_Preview() {
    val rowModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .padding(16.dp)
        .background(MaterialTheme.colorScheme.surface)
    NeevaTheme {
        SettingsLinkRow(title = "A Label", openUrl = {}, Uri.EMPTY, rowModifier)
    }
}

@Preview(name = "Settings Link Row Dark, 1x font size", locale = "en")
@Preview(name = "Settings Link Row Dark, 2x font size", locale = "en", fontScale = 2.0f)
@Preview(name = "Settings Link Row Dark, RTL, 1x font size", locale = "he")
@Preview(name = "Settings Link Row Dark, RTL, 2x font size", locale = "he", fontScale = 2.0f)
@Composable
fun SettingsLinkRow_Dark_Preview() {
    val rowModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .padding(16.dp)
        .background(MaterialTheme.colorScheme.surface)
    NeevaTheme(useDarkTheme = true) {
        SettingsLinkRow(title = "A Label", openUrl = {}, Uri.EMPTY, rowModifier)
    }
}