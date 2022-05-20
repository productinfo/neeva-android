package com.neeva.app.cookiecutter

import android.net.Uri
import androidx.compose.runtime.State
import com.neeva.app.browsing.getBrowserIfAlive
import com.neeva.app.cookiecutter.TrackingEntity.Companion.trackingEntityForHost
import com.neeva.app.publicsuffixlist.DomainProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.chromium.weblayer.Tab

data class TrackingData(
    val numTrackers: Int,
    val numDomains: Int,
    val trackingEntities: Map<TrackingEntity, Int>
) {
    companion object {
        fun create(stats: Map<String, Int>?, domainProvider: DomainProvider): TrackingData {
            val numTrackers: Int = stats?.values?.sum() ?: 0
            val numDomains: Int = stats?.keys?.count() ?: 0
            val trackingEntities: Map<TrackingEntity, Int> =
                mutableMapOf<TrackingEntity, Int>().apply {
                    stats?.forEach {
                        val trackingEntity = trackingEntityForHost(
                            domainProvider.getRegisteredDomain(Uri.parse(it.key))
                        )
                        if (trackingEntity != null) {
                            put(trackingEntity, (get(trackingEntity) ?: 0) + it.value)
                        }
                    }
                }

            return TrackingData(
                numTrackers = numTrackers,
                numDomains = numDomains,
                trackingEntities = trackingEntities
            )
        }
    }
}

class TabCookieCutterModel(
    val tab: Tab,
    private val trackingDataFlow: MutableStateFlow<TrackingData?>,
    private val enableTrackingProtection: State<Boolean>,
    val domainProvider: DomainProvider
) {
    /** When true, the tab will be reloaded when it becomes active tab. */
    var reloadUponForeground = false

    private var stats: Map<String, Int>? = null
        // TODO(kobec/chung): when darin fixes the stopFiltering() method, remove this get()
        get() {
            // Because ContentFilterManager.stopFiltering() doesn't stop
            // onContentFilterStatsUpdated() from being called, we need to return null when the
            // Tracking Protection toggle is disabled.
            return if (enableTrackingProtection.value) {
                field
            } else {
                null
            }
        }
        set(value) {
            field = value
            if (tab.getBrowserIfAlive()?.activeTab == tab) {
                trackingDataFlow.value = TrackingData.create(stats, domainProvider)
            }
        }

    fun whoIsTrackingYouHosts(trackingData: TrackingData): Map<TrackingEntity, Int> {
        return trackingData.trackingEntities
            .toList()
            .sortedByDescending { (_, value) -> value }
            .take(3)
            .toMap()
    }

    fun currentTrackingData(): TrackingData? {
        return TrackingData.create(stats, domainProvider)
    }

    fun resetStat() {
        stats = null
    }

    fun updateStats(stats: Map<String, Int>?) {
        this.stats = stats
    }
}
