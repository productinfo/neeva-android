// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.storage.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.neeva.app.NeevaConstants
import com.neeva.app.storage.entities.HostInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface HostInfoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(hostInfo: HostInfo)

    @Query("DELETE FROM HostInfo WHERE isTrackingAllowed = 1")
    suspend fun deleteTrackingAllowedHosts()

    @Query("DELETE FROM HostInfo WHERE host = :host")
    suspend fun deleteFromHostInfo(host: String)

    @Query("SELECT * FROM HostInfo WHERE isTrackingAllowed = 1")
    suspend fun getAllTrackingAllowedHosts(): List<HostInfo>

    @Query("SELECT * FROM HostInfo WHERE host = :host")
    suspend fun getHostInfoByName(host: String): HostInfo?

    @Query("SELECT * FROM HostInfo WHERE host = :host")
    fun getHostInfoByNameFlow(host: String): Flow<HostInfo?>

    @Update
    suspend fun update(hostInfo: HostInfo)

    @Transaction
    suspend fun upsert(hostInfo: HostInfo) {
        when (getHostInfoByName(hostInfo.host)) {
            null -> add(hostInfo)
            else -> update(hostInfo)
        }
    }

    @Transaction
    suspend fun toggleTrackingAllowedForHost(host: String): Boolean {
        val isTrackingCurrentlyAllowed = getHostInfoByName(host)?.isTrackingAllowed ?: false

        return if (isTrackingCurrentlyAllowed) {
            deleteFromHostInfo(host)
            true
        } else {
            upsert(HostInfo(host = host, isTrackingAllowed = true))
            false
        }
    }

    suspend fun initializeForFirstRun(neevaConstants: NeevaConstants) {
        // Initialize Cookie Cutter with an exclusion for the homepage.
        // Although we could do this using a Room callback when the database is first created,
        // it's safer to rely on using the HostInfoDao to do it directly rather than use raw SQL
        // statements that don't get validated by Room, which can break if anyone ever updates the
        // HostInfo class.
        upsert(
            HostInfo(
                host = neevaConstants.appHost,
                isTrackingAllowed = true
            )
        )
    }
}
