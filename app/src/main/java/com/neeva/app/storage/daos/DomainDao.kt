package com.neeva.app.storage.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.neeva.app.storage.entities.Domain
import com.neeva.app.storage.entities.Favicon
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {
    @Query("SELECT * FROM domain")
    fun getAll(): Flow<List<Domain>>

    @Query("SELECT * FROM domain WHERE domainName LIKE :domainName")
    suspend fun get(domainName: String): Domain?

    // Returns list of all domains that has a domainName containing the query
    @Query("SELECT * FROM domain WHERE domainName LIKE :query||'%' LIMIT :limit")
    suspend fun matchesTo(query: String, limit: Int): List<Domain>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(vararg domains: Domain)

    @Update
    suspend fun update(vararg domains: Domain)

    @Delete
    suspend fun delete(domain: Domain)

    @Transaction
    suspend fun updateFavicon(domainName: String, favicon: Favicon) {
        val domain = get(domainName)
        if (domain == null) {
            add(
                Domain(
                    domainName = domainName,
                    providerName = null,
                    largestFavicon = favicon
                )
            )
        } else {
            // Take the largest available favicon, which doesn't necessarily make sense for sites
            // with pages that have different favicons on them (e.g. google.com vs news.google.com).
            val bestFavicon = when {
                domain.largestFavicon == null -> favicon
                favicon.width >= domain.largestFavicon.width -> favicon
                else -> domain.largestFavicon
            }
            update(
                domain.copy(
                    domainName = domainName,
                    largestFavicon = bestFavicon
                )
            )
        }
    }

    @Transaction
    suspend fun upsert(domain: Domain) {
        if (get(domain.domainName) == null) {
            add(domain)
        } else {
            update(domain)
        }
    }
}