package com.neeva.app.storage.daos

import android.net.Uri
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.neeva.app.storage.entities.Favicon
import com.neeva.app.storage.entities.Site
import com.neeva.app.storage.entities.Visit
import java.util.Date
import kotlinx.coroutines.flow.Flow

@RewriteQueriesToDropUnusedColumns
@Dao
interface SitesWithVisitsAccessor : SiteDao, VisitDao {
    @Query(
        """
        SELECT *
        FROM site INNER JOIN visit ON site.siteUID = visit.visitedSiteUID
        WHERE visit.timestamp >= :startTime AND visit.timestamp < :endTime 
        ORDER BY visit.timestamp DESC
        """
    )
    fun getPagedSitesVisitedBetween(startTime: Date, endTime: Date): PagingSource<Int, Site>

    @Query(
        """
        SELECT *
        FROM site INNER JOIN visit ON site.siteUID = visit.visitedSiteUID
        WHERE visit.timestamp >= :thresholdTime
        ORDER BY visit.timestamp DESC
        """
    )
    fun getPagedSitesVisitedAfter(thresholdTime: Date): PagingSource<Int, Site>

    /** Deletes any entries in the [Site] table that have no corresponding [Visit] information. */
    @Query(
        """
            DELETE
            FROM site
            WHERE site.siteUID IN (
                SELECT site.siteUID
                FROM site LEFT JOIN visit ON site.siteUID = visit.visitedSiteUID
                WHERE visit.visitUID IS NULL
                GROUP BY site.siteUID
            )
        """
    )
    fun deleteOrphanedSiteEntities(): Int

    @Query(
        """
        SELECT *
        FROM Site INNER JOIN Visit ON Site.siteUID = Visit.visitedSiteUID
        WHERE visit.timestamp >= :thresholdTime
        GROUP BY site.siteUID
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """
    )
    fun getFrequentSitesAfter(thresholdTime: Date, limit: Int = 10): List<Site>

    @Query(
        """
        SELECT *
        FROM Site INNER JOIN Visit ON Site.siteUID = Visit.visitedSiteUID
        WHERE visit.timestamp >= :thresholdTime
        GROUP BY site.siteUID
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """
    )
    fun getFrequentSitesAfterFlow(thresholdTime: Date, limit: Int = 10): Flow<List<Site>>

    @Query(
        """
        SELECT *
        FROM Site INNER JOIN Visit ON Site.siteUID = Visit.visitedSiteUID
        WHERE Site.siteURL LIKE '%'||:query||'%'
        GROUP BY Site.siteUID
        ORDER BY COUNT(*) DESC
        LIMIT :limit
    """
    )
    suspend fun getQuerySuggestions(query: String, limit: Int): List<Site>

    @Transaction
    suspend fun upsert(
        url: Uri,
        title: String? = null,
        favicon: Favicon? = null,
        visit: Visit? = null
    ) {
        var site = getSiteByUrl(url.toString())

        if (site != null) {
            // Don't replace the title if we are trying to update an existing entry because we may
            // be trying to update an entry from a place where it's not available.
            site = site.copy(
                title = title.takeUnless { title.isNullOrBlank() } ?: site.title,
                largestFavicon = favicon ?: site.largestFavicon
            )

            updateSite(site)
        } else {
            site = Site(
                siteURL = url.toString(),
                title = title,
                largestFavicon = favicon
            )

            addSite(site)
        }

        visit?.let {
            addVisit(
                it.copy(visitedSiteUID = getSiteByUrl(site.siteURL)?.siteUID ?: 0)
            )
        }
    }

    @Transaction
    suspend fun deleteHistoryWithinTimeframe(startTime: Date, endTime: Date) {
        deleteVisitsWithinTimeframe(startTime, endTime)
        deleteOrphanedSiteEntities()
    }
}