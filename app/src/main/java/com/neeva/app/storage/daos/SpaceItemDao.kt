// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.storage.daos

import android.net.Uri
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.neeva.app.storage.entities.SpaceItem

@Dao
interface SpaceItemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSpaceItem(vararg spaceItem: SpaceItem)

    @Query("DELETE FROM spaceItem")
    suspend fun deleteAllSpaceItems()

    @Update
    suspend fun updateSpaceItem(vararg spaceItem: SpaceItem)

    @Query("SELECT * FROM spaceItem WHERE id = :id")
    suspend fun getSpaceItemById(id: String): SpaceItem?

    @Transaction
    suspend fun upsert(spaceItem: SpaceItem) {
        when (getSpaceItemById(spaceItem.id)) {
            null -> addSpaceItem(spaceItem)
            else -> updateSpaceItem(spaceItem)
        }
    }

    @Delete
    suspend fun deleteSpaceItem(vararg spaceItem: SpaceItem)

    @Query("SELECT * FROM spaceItem WHERE spaceID = :spaceID ORDER BY itemIndex ASC")
    suspend fun getItemsFromSpace(spaceID: String): List<SpaceItem>

    @Query("SELECT DISTINCT spaceID FROM spaceItem WHERE url = :url")
    suspend fun getSpaceIDsWithURL(url: Uri?): List<String>
}
