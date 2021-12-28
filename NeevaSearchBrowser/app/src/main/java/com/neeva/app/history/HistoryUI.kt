package com.neeva.app.history

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.neeva.app.R
import com.neeva.app.browsing.toFaviconBitmap
import com.neeva.app.storage.Site
import com.neeva.app.suggestions.NavSuggestion
import com.neeva.app.ui.theme.NeevaTheme
import com.neeva.app.widgets.CollapsingState
import com.neeva.app.widgets.collapsibleHeaderItems
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

@Composable
fun HistoryUI(
    history: List<Site>,
    onClose: () -> Unit,
    onOpenUrl: (Uri) -> Unit,
    faviconProvider: (Uri?) -> LiveData<Bitmap?>,
    now: LocalDate = LocalDate.now()
) {
    // Bucket the history by their timestamps.
    // TODO(dan.alcantara): This Composable will only fire when the history database is updated, so
    //                      if the user visits the history UI multiple times over several days
    //                      without visiting anywhere else, it won't properly update.
    val startOf7DaysAgo = Date.from(now.minusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC))
    val startOfYesterday = Date.from(now.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
    val startOfToday = Date.from(now.atStartOfDay().toInstant(ZoneOffset.UTC))
    val startOfTomorrow = Date.from(now.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))

    val historyToday = history
        .filter { it.lastVisitTimestamp >= startOfToday && it.lastVisitTimestamp < startOfTomorrow }
        .map { it.toNavSuggestion() }

    val historyYesterday = history
        .filter { it.lastVisitTimestamp >= startOfYesterday && it.lastVisitTimestamp < startOfToday }
        .map { it.toNavSuggestion() }

    val historyThisWeek = history
        .filter { it.lastVisitTimestamp >= startOf7DaysAgo && it.lastVisitTimestamp < startOfYesterday }
        .map { it.toNavSuggestion() }

    // Compose doesn't count `LazyListScope` as a @Composable, so pull the header strings here.
    val headerToday = stringResource(id = R.string.history_today)
    val headerYesterday = stringResource(id = R.string.history_yesterday)
    val headerThisWeek = stringResource(id = R.string.history_this_week)

    Column(
        modifier = Modifier
            .background(MaterialTheme.colors.background)
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colors.primary)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_baseline_arrow_back_24),
                contentDescription = stringResource(R.string.close),
                contentScale = ContentScale.Inside,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onClose() },
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary)
            )
            Text(
                text = stringResource(R.string.history),
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.h3,
                color = MaterialTheme.colors.onPrimary,
                maxLines = 1,
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
        ) {
            collapsibleHeaderItems(
                headerToday,
                CollapsingState.SHOW_COMPACT,
                items = historyToday
            ) {
                val bitmap: Bitmap? by faviconProvider(it.url).observeAsState()
                NavSuggestion(bitmap, onOpenUrl, it)
            }

            collapsibleHeaderItems(
                headerYesterday,
                CollapsingState.SHOW_COMPACT,
                items = historyYesterday
            ) {
                val bitmap: Bitmap? by faviconProvider(it.url).observeAsState()
                NavSuggestion(bitmap, onOpenUrl, it)
            }

            collapsibleHeaderItems(
                headerThisWeek,
                CollapsingState.SHOW_COMPACT,
                items = historyThisWeek
            ) {
                val bitmap: Bitmap? by faviconProvider(it.url).observeAsState()
                NavSuggestion(bitmap, onOpenUrl, it)
            }
        }
    }
}

@Preview
@Composable
fun HistoryUI_Preview() {
    val history = mutableListOf<Site>()

    val now = LocalDate.now()
    var ids = 0

    // Add items for today.
    for (i in 0 until 2) {
        history.add(
            Site(
                siteUID = ids++,
                siteURL = "https://www.site$ids.com/$i",
                lastVisitTimestamp = Date.from(
                    now.atStartOfDay().plusHours(i.toLong()).toInstant(ZoneOffset.UTC)
                ),
                metadata = null,
                largestFavicon = null
            )
        )
    }

    // Add items for yesterday.
    for (i in 0 until 2) {
        history.add(
            Site(
                siteUID = ids++,
                siteURL = "https://www.site$ids.com/$i",
                lastVisitTimestamp = Date.from(
                    now.minusDays(1).atStartOfDay().plusHours(i.toLong()).toInstant(ZoneOffset.UTC)
                ),
                metadata = null,
                largestFavicon = null
            )
        )
    }

    // Add one item for each day before that.  Items that are too old should not be displayed.
    for (daysAgo in 2 until 10) {
        history.add(
            Site(
                siteUID = ids++,
                siteURL = "https://www.site$ids.com/${daysAgo}_days_ago",
                lastVisitTimestamp = Date.from(
                    now.minusDays(daysAgo.toLong()).atStartOfDay().toInstant(ZoneOffset.UTC)
                ),
                metadata = null,
                largestFavicon = null
            )
        )
    }

    // The database returns these in descending order.
    history.sortByDescending { it.lastVisitTimestamp }

    NeevaTheme {
        HistoryUI(
            history = history,
            onClose = {},
            onOpenUrl = {},
            faviconProvider = { MutableLiveData(it.toFaviconBitmap()) },
            now = now
        )
    }
}