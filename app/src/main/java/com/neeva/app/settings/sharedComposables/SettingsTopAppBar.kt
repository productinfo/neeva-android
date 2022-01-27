package com.neeva.app.settings

import androidx.compose.foundation.Image
import androidx.compose.material.IconButton
import androidx.compose.material.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.neeva.app.R
import com.neeva.app.ui.theme.NeevaTheme

@Composable
fun SettingsTopAppBar(title: String, onBackPressed: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge
            )
        },
        backgroundColor = MaterialTheme.colorScheme.surface,
        navigationIcon = {
            IconButton(
                onClick = { onBackPressed() }
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_baseline_arrow_back_24),
                    contentDescription = stringResource(R.string.close),
                    contentScale = ContentScale.Inside,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    )
}

@Preview(name = "Settings Top App Bar, 1x font size", locale = "en")
@Preview(name = "Settings Top App Bar, 2x font size", locale = "en", fontScale = 2.0f)
@Preview(name = "Settings Top App Bar, RTL, 1x font size", locale = "he")
@Preview(name = "Settings Top App Bar, RTL, 2x font size", locale = "he", fontScale = 2.0f)
@Composable
fun SettingsTopAppBar_Preview() {
    NeevaTheme {
        SettingsTopAppBar(
            title = stringResource(R.string.debug_long_string_primary),
            onBackPressed = {}
        )
    }
}

@Preview(name = "Settings Top App Bar Dark, 1x font size", locale = "en")
@Preview(name = "Settings Top App Bar Dark, 2x font size", locale = "en", fontScale = 2.0f)
@Preview(name = "Settings Top App Bar Dark, RTL, 1x font size", locale = "he")
@Preview(name = "Settings Top App Bar Dark, RTL, 2x font size", locale = "he", fontScale = 2.0f)
@Composable
fun SettingsTopAppBar_Dark_Preview() {
    NeevaTheme(useDarkTheme = true) {
        SettingsTopAppBar(
            title = stringResource(R.string.debug_long_string_primary),
            onBackPressed = {}
        )
    }
}