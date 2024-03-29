// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.spaces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.apollographql.apollo3.api.Optional
import com.neeva.app.AddSpacePublicACLMutation
import com.neeva.app.AddToSpaceMutation
import com.neeva.app.BatchDeleteSpaceResultMutation
import com.neeva.app.CreateSpaceMutation
import com.neeva.app.DeleteSpaceMutation
import com.neeva.app.DeleteSpacePublicACLMutation
import com.neeva.app.DeleteSpaceResultByURLMutation
import com.neeva.app.Dispatchers
import com.neeva.app.GetSpacesDataQuery
import com.neeva.app.LeaveSpaceMutation
import com.neeva.app.ListSpacesQuery
import com.neeva.app.NeevaConstants
import com.neeva.app.R
import com.neeva.app.UpdateSpaceEntityDisplayDataMutation
import com.neeva.app.UpdateSpaceMutation
import com.neeva.app.apollo.AuthenticatedApolloWrapper
import com.neeva.app.apollo.UnauthenticatedApolloWrapper
import com.neeva.app.appnav.AppNavDestination
import com.neeva.app.storage.BitmapIO
import com.neeva.app.storage.Directories
import com.neeva.app.storage.HistoryDatabase
import com.neeva.app.storage.entities.Space
import com.neeva.app.storage.entities.SpaceItem
import com.neeva.app.storage.entities.spaceItem
import com.neeva.app.storage.scaleDownMaintainingAspectRatio
import com.neeva.app.storage.toByteArray
import com.neeva.app.type.AddSpacePublicACLInput
import com.neeva.app.type.AddSpaceResultByURLInput
import com.neeva.app.type.BatchDeleteSpaceResultInput
import com.neeva.app.type.DeleteSpaceInput
import com.neeva.app.type.DeleteSpacePublicACLInput
import com.neeva.app.type.DeleteSpaceResultByURLInput
import com.neeva.app.type.LeaveSpaceInput
import com.neeva.app.type.SpaceACLLevel
import com.neeva.app.type.UpdateSpaceEntityDisplayDataInput
import com.neeva.app.type.UpdateSpaceInput
import com.neeva.app.ui.PopupModel
import com.neeva.app.userdata.NeevaUser
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Manages interactions with the user's Spaces. */
class SpaceStore(
    private val appContext: Context,
    historyDatabase: HistoryDatabase,
    private val coroutineScope: CoroutineScope,
    private val unauthenticatedApolloWrapper: UnauthenticatedApolloWrapper,
    private val authenticatedApolloWrapper: AuthenticatedApolloWrapper,
    private val neevaUser: NeevaUser,
    private val neevaConstants: NeevaConstants,
    private val popupModel: PopupModel,
    private val dispatchers: Dispatchers,
    directories: Directories
) {
    companion object {
        private const val DIRECTORY_NAME = "spaces"
        private const val MAX_THUMBNAIL_SIZE = 300
        const val MAKER_COMMUNITY_SPACE_ID = "xlvaUJmdPRSrcqRHPEzVPuWf4RP74EyHvz5QvxLN"

        fun createAddToSpaceMutation(
            space: Space,
            url: Uri,
            title: String,
            description: String? = null
        ) = AddToSpaceMutation(
            input = AddSpaceResultByURLInput(
                spaceID = space.id,
                url = url.toString(),
                title = title,
                data = description?.let { Optional.presentIfNotNull(it) }
                    ?: Optional.Absent,
                mediaType = Optional.presentIfNotNull("text/plain")
            )
        )

        fun createDeleteSpaceResultByURLMutation(
            space: Space,
            uri: Uri
        ) = DeleteSpaceResultByURLMutation(
            input = DeleteSpaceResultByURLInput(
                spaceID = space.id,
                url = uri.toString(),
            )
        )
    }

    enum class State {
        READY,
        REFRESHING,
        UPDATING_DB_AFTER_MUTATION,
        FAILED
    }

    private val dao = historyDatabase.spaceDao()

    /** This ID determines what to show when we are at [AppNavDestination.SPACE_DETAIL] */
    val detailedSpaceIDFlow = MutableStateFlow<String?>(null)
    val fetchedSpaceFlow = detailedSpaceIDFlow
        .filterNotNull()
        .map { id ->
            dao.getSpaceById(id)?.let { return@map null }

            val response = unauthenticatedApolloWrapper.performQuery(
                GetSpacesDataQuery(Optional.presentIfNotNull(listOf(id))), false
            ).response
            val space = response?.data?.getSpace?.space?.first() ?: return@map null
            space.pageMetadata?.pageID?.let { pageID ->
                val name = space.space?.name ?: return@let null
                val entityQueries = space.space.entities ?: return@let null

                // We push the SpaceItems to the DB for now, but not the Space. This will avoid
                // caching the not-yet-followed Space and we will clean up the SpaceItems because
                // they are orphaned (their corresponding Space is not in DB)
                updateSpaceEntities(pageID, entityQueries)

                return@let Space(
                    id = pageID,
                    name = name,
                    description = space.space.description ?: "",
                    lastModifiedTs = "",
                    thumbnail = null,
                    resultCount = 0,
                    isDefaultSpace = false,
                    isShared = false,
                    isPublic = true,
                    userACL = SpaceACLLevel.PublicView,
                    ownerName = space.space.owner?.displayName ?: "",
                    ownerPictureURL = space.space.owner?.pictureURL?.let { Uri.parse(it) },
                    numViews = space.stats?.views ?: 0,
                    numFollowers = space.stats?.followers ?: 0
                )
            }
        }.flowOn(dispatchers.io)
        .stateIn(coroutineScope, SharingStarted.Lazily, null)
        .filterNotNull()

    val allSpacesFlow = dao.allSpacesFlow()
        .flowOn(dispatchers.io)
        .distinctUntilChanged()
        .stateIn(coroutineScope, SharingStarted.Lazily, emptyList())
    val editableSpacesFlow = allSpacesFlow
        .map { it.filterNot { space -> space.userACL >= SpaceACLLevel.Edit } }
    val stateFlow = MutableStateFlow(State.READY)

    val spacesFromCommunityFlow: MutableStateFlow<List<SpaceRowData>> =
        MutableStateFlow(emptyList())

    @VisibleForTesting
    val spacesDirectory: Deferred<File> = directories.cacheSubdirectoryAsync(DIRECTORY_NAME)

    private var isRefreshPending: Boolean = false
    private var cleanUpThumbnails: Boolean = true
    private var lastFetchedSpaceIds = emptyList<String>()

    suspend fun spaceStoreContainsUrl(url: Uri): Boolean = spaceIDsContainingURL(url).isNotEmpty()

    suspend fun deleteAllData() {
        dao.deleteAllSpaces()
        dao.deleteAllSpaceItems()
        cleanupSpacesThumbnails()
        lastFetchedSpaceIds = emptyList()
    }

    suspend fun refresh() {
        if (neevaUser.isSignedOut()) {
            fetchCommunitySpaces(appSpacesURL = neevaConstants.appSpacesURL)
            return
        }
        // TODO(yusuf) : Early return here if there is no connectivity

        if (stateFlow.value == State.REFRESHING) {
            isRefreshPending = true
            return
        }

        stateFlow.value = State.REFRESHING

        val succeeded = performRefresh()

        stateFlow.value = if (succeeded) {
            State.READY
        } else {
            State.FAILED
        }

        withContext(dispatchers.io) {
            cleanupDatabaseAfterRefresh()
        }

        if (cleanUpThumbnails) {
            cleanUpThumbnails = false
            withContext(dispatchers.io) {
                cleanupSpacesThumbnails()
            }
        }

        if (isRefreshPending) {
            isRefreshPending = false
            refresh()
        }
    }

    private suspend fun fetchCommunitySpaces(appSpacesURL: String) = withContext(dispatchers.io) {
        val response =
            unauthenticatedApolloWrapper.performQuery(
                GetSpacesDataQuery(Optional.presentIfNotNull(listOf(MAKER_COMMUNITY_SPACE_ID))),
                userMustBeLoggedIn = false
            ).response

        val entities = response?.data?.getSpace?.space?.first()?.space?.entities
            ?.filter {
                it.spaceEntity?.url?.startsWith(appSpacesURL) == true &&
                    it.spaceEntity.title?.isNotEmpty() == true &&
                    Uri.parse(it.spaceEntity.url).pathSegments.size == 2
            }
            ?: return@withContext
        val space = response.data?.getSpace?.space?.first()?.let {
            Space(
                id = MAKER_COMMUNITY_SPACE_ID,
                name = appContext.getString(R.string.community_spaces),
                description = it.space?.description ?: "",
                lastModifiedTs = "",
                thumbnail = null,
                resultCount = entities.count(),
                isDefaultSpace = false,
                isShared = false,
                isPublic = true,
                userACL = SpaceACLLevel.PublicView,
                numFollowers = it.stats?.followers ?: 0,
                ownerName = it.space?.owner?.displayName ?: "",
                ownerPictureURL = it.space?.owner?.pictureURL?.let { uri -> Uri.parse(uri) }
            )
        } ?: return@withContext

        dao.upsert(space)
        updateSpaceEntities(MAKER_COMMUNITY_SPACE_ID, entities)

        spacesFromCommunityFlow.emit(
            entities.map {
                val spaceData = SpaceRowData(
                    id = Uri.parse(it.spaceEntity?.url!!).pathSegments[1],
                    name = it.spaceEntity.title!!,
                    thumbnail = it.spaceEntity.thumbnail?.let {
                        thumbnailUri ->
                        Uri.parse(thumbnailUri)
                    },
                    isPublic = true,
                    appSpacesURL = appSpacesURL
                )
                if (spaceData.thumbnail == null) {
                    spaceData.thumbnail = saveBitmap(
                        directory = spacesDirectory.await().resolve(MAKER_COMMUNITY_SPACE_ID),
                        dispatchers = dispatchers,
                        id = spaceData.id,
                        bitmapString = it.spaceEntity.thumbnail
                    )?.toUri()
                }
                return@map spaceData
            }
        )
    }

    private suspend fun performRefresh(): Boolean = withContext(dispatchers.io) {
        val response =
            authenticatedApolloWrapper.performQuery(
                ListSpacesQuery(), userMustBeLoggedIn = true
            ).response?.data ?: return@withContext false

        // If there are no spaces to process, but the response was fine, just indicate success.
        val listSpaces = response.listSpaces ?: return@withContext true
        val oldSpaceMap = dao.allSpaces().associateBy { it.id }

        // Fetch all the of the user's Spaces.
        val spacesToFetch = mutableListOf<Space>()

        lastFetchedSpaceIds = listSpaces.space
            .map { listSpacesQuery ->
                val newSpace = listSpacesQuery.toSpace(
                    neevaUser.userInfoFlow.value?.id
                ) ?: return@map null

                val oldSpace = oldSpaceMap[newSpace.id]
                newSpace.thumbnail = oldSpace?.thumbnail
                if (oldSpace == null || oldSpace.lastModifiedTs != newSpace.lastModifiedTs) {
                    spacesToFetch.add(newSpace)
                }
                dao.upsert(newSpace)

                newSpace
            }
            .mapNotNull { it?.id }

        return@withContext performFetch(spacesToFetch)
    }

    private suspend fun performFetch(
        spacesToFetch: List<Space>
    ): Boolean = withContext(dispatchers.io) {
        if (spacesToFetch.isEmpty()) return@withContext true

        // Get updated data for any Spaces that have changed since the last fetch.
        val spacesDataResponse = authenticatedApolloWrapper.performQuery(
            GetSpacesDataQuery(Optional.presentIfNotNull(spacesToFetch.map { it.id })),
            userMustBeLoggedIn = true
        ).response?.data ?: return@withContext false

        spacesDataResponse.getSpace?.space?.forEach { spaceQuery ->
            val spaceID = spaceQuery.pageMetadata?.pageID ?: return@forEach
            val entityQueries = spaceQuery.space?.entities ?: return@forEach

            val entities = updateSpaceEntities(spaceID, entityQueries)

            dao.getItemsFromSpace(spaceID)
                .filterNot { entities.contains(it) }
                .forEach { dao.deleteSpaceItem(it) }

            spacesToFetch
                .firstOrNull { space -> space.id == spaceID }
                ?.let { space ->
                    space.thumbnail = saveBitmap(
                        directory = spacesDirectory.await().resolve(space.id),
                        dispatchers = dispatchers,
                        id = spaceID,
                        bitmapString = spaceQuery.space.thumbnail
                    )?.toUri()
                    dao.upsert(space)
                }
        }

        return@withContext true
    }

    private suspend fun updateSpaceEntities(
        spaceID: String,
        entityQueries: List<GetSpacesDataQuery.Entity>
    ): List<SpaceItem> {
        val entities =
            entityQueries
                .filter { it.metadata?.docID != null }
                .mapNotNull { entityQuery ->
                    val thumbnailUri = entityQuery.spaceEntity?.thumbnail?.let {
                        saveBitmap(
                            directory = spacesDirectory.await().resolve(spaceID),
                            dispatchers = dispatchers,
                            id = entityQuery.metadata!!.docID!!,
                            bitmapString = it
                        )?.toUri()
                    }
                    entityQuery.spaceItem(spaceID, thumbnailUri)
                }
        entities.withIndex().forEach {
            it.value.itemIndex = it.index
        }
        entities.forEach { dao.upsert(it) }
        return entities
    }

    private suspend fun contentURLsForSpace(spaceID: String) =
        contentDataForSpace(spaceID = spaceID).mapNotNull { it.url }

    /** Returns all the items within a given Space. */
    suspend fun contentDataForSpace(spaceID: String) =
        dao.getItemsFromSpace(spaceID = spaceID)

    suspend fun spaceIDsContainingURL(url: Uri?) = dao.getSpaceIDsWithURL(url = url)

    /** Cleans up the [Space] and [SpaceItem] tables by taking [allSpacesFlow] as source of truth */
    private suspend fun cleanupDatabaseAfterRefresh() {
        dao.allSpaceIds()
            .filterNot { lastFetchedSpaceIds.contains(it) }
            .forEach { dao.deleteSpaceById(it) }
        dao.deleteOrphanedSpaceItems()
    }

    suspend fun addOrRemoveFromSpace(
        spaceID: String,
        url: Uri,
        title: String,
        description: String? = null,
        onOpenSpace: (String) -> Unit = {}
    ): Boolean = withContext(dispatchers.io) {
        val space = dao.getSpaceById(spaceID) ?: return@withContext false

        return@withContext if (contentURLsForSpace(space.id).contains(url)) {
            removeFromSpace(space, url)
        } else {
            addToSpace(space, url, title, description, onOpenSpace)
        }
    }

    private suspend fun cleanupSpacesThumbnails() {
        val idList = dao.allSpaceIds()
        val thumbnailDirectory = spacesDirectory.await()
        thumbnailDirectory
            .list { file, _ ->
                file.isDirectory
            }?.filterNot { folderName ->
                folderName == MAKER_COMMUNITY_SPACE_ID || idList.contains(folderName)
            }?.forEach {
                thumbnailDirectory.resolve(it).deleteRecursively()
            }
    }

    suspend fun addToSpace(
        space: Space,
        url: Uri,
        title: String,
        description: String? = null,
        onOpenSpace: (String) -> Unit = {}
    ): Boolean = withContext(dispatchers.io) {
        val spaceID = space.id
        stateFlow.value = State.UPDATING_DB_AFTER_MUTATION
        val response = authenticatedApolloWrapper.performMutation(
            createAddToSpaceMutation(space, url, title, description),
            userMustBeLoggedIn = true
        ).response

        return@withContext response?.data?.entityId?.let {
            Timber.i("Added item to space with id=$it")
            popupModel.showSnackbar(
                message = appContext.getString(R.string.space_added_url_to_space, space.name),
                actionLabel = appContext.getString(R.string.space_open),
                onActionPerformed = { onOpenSpace(spaceID) }
            )
            dao.upsert(
                SpaceItem(
                    id = it,
                    spaceID = space.id,
                    url = url,
                    title = title,
                    snippet = description,
                    thumbnail = null
                )
            )
            stateFlow.value = State.READY
            true
        } ?: run {
            val errorString = appContext.getString(R.string.error_generic)
            popupModel.showSnackbar(errorString)
            stateFlow.value = State.READY
            false
        }
    }

    fun updateSpaceItem(item: SpaceItem, title: String, description: String) {
        coroutineScope.launch(dispatchers.io) {
            stateFlow.value = State.UPDATING_DB_AFTER_MUTATION
            val response = authenticatedApolloWrapper.performMutation(
                UpdateSpaceEntityDisplayDataMutation(
                    UpdateSpaceEntityDisplayDataInput(
                        spaceID = Optional.presentIfNotNull(item.spaceID),
                        resultID = Optional.presentIfNotNull(item.id),
                        title = Optional.presentIfNotNull(title),
                        snippet = Optional.presentIfNotNull(description)
                    )
                ),
                userMustBeLoggedIn = true
            ).response

            response?.data?.let {
                Timber.i("Updated space item with id=${item.id}")
                dao.upsert(item.copy(title = title, snippet = description))
            }
            stateFlow.value = State.READY
        }
    }

    fun updateSpace(space: Space, title: String, description: String) {
        coroutineScope.launch(dispatchers.io) {
            stateFlow.value = State.UPDATING_DB_AFTER_MUTATION
            val response = authenticatedApolloWrapper.performMutation(
                UpdateSpaceMutation(
                    UpdateSpaceInput(
                        id = space.id,
                        name = Optional.presentIfNotNull(title),
                        description = Optional.presentIfNotNull(description)
                    )
                ),
                userMustBeLoggedIn = true
            ).response

            response?.data?.let {
                Timber.i("Updated space with id=${space.id}")
                dao.upsert(space.copy(name = title, description = description))
            }
            stateFlow.value = State.READY
        }
    }

    fun removeFromSpace(item: SpaceItem) {
        coroutineScope.launch(dispatchers.io) {
            stateFlow.value = State.UPDATING_DB_AFTER_MUTATION
            val response = authenticatedApolloWrapper.performMutation(
                BatchDeleteSpaceResultMutation(
                    BatchDeleteSpaceResultInput(
                        spaceID = item.spaceID,
                        resultIDs = listOf(item.id)
                    )
                ),
                userMustBeLoggedIn = true
            ).response

            response?.data?.let { dao.deleteSpaceItem(item) }
            stateFlow.value = State.READY
        }
    }

    private suspend fun removeFromSpace(space: Space, uri: Uri): Boolean {
        val spaceID = space.id
        stateFlow.value = State.UPDATING_DB_AFTER_MUTATION
        val response = authenticatedApolloWrapper.performMutation(
            createDeleteSpaceResultByURLMutation(space, uri),
            userMustBeLoggedIn = true
        ).response

        return response?.data?.deleteSpaceResultByURL?.let {
            val successString =
                appContext.getString(R.string.space_removed_url_from_space, space.name)
            Timber.i(successString)
            popupModel.showSnackbar(successString)
            val spaceItem = dao.getItemsFromSpace(spaceID).find { it.url == uri }
            spaceItem?.let { dao.deleteSpaceItem(it) }
            stateFlow.value = State.READY
            true
        } ?: run {
            val errorString = appContext.getString(R.string.error_generic)
            popupModel.showSnackbar(errorString)
            stateFlow.value = State.READY
            false
        }
    }

    fun deleteOrUnfollowSpace(spaceId: String) {
        coroutineScope.launch(dispatchers.io) {
            val space = dao.getSpaceById(spaceId) ?: return@launch

            stateFlow.value = State.UPDATING_DB_AFTER_MUTATION

            val mutation = when (space.userACL == SpaceACLLevel.Owner) {
                true -> DeleteSpaceMutation(
                    input = DeleteSpaceInput(
                        id = space.id
                    )
                )

                false -> LeaveSpaceMutation(
                    input = LeaveSpaceInput(
                        id = space.id
                    )
                )
            }
            val response = authenticatedApolloWrapper.performMutation(
                mutation,
                userMustBeLoggedIn = true
            ).response

            response?.data?.let {
                dao.deleteSpace(space)
            } ?: run {
                val errorString = appContext.getString(R.string.error_generic)
                popupModel.showSnackbar(errorString)
            }
            stateFlow.value = State.READY
        }
    }

    fun createSpace(
        spaceName: String,
        promptToOpenSpace: Boolean,
        onOpenSpace: (String) -> Unit
    ) {
        coroutineScope.launch(dispatchers.io) {
            val response = authenticatedApolloWrapper.performMutation(
                CreateSpaceMutation(name = spaceName),
                userMustBeLoggedIn = true
            ).response

            response?.data?.createSpace?.let {
                popupModel.showSnackbar(
                    message = appContext.getString(R.string.space_create_success, spaceName),
                    actionLabel = appContext
                        .getString(R.string.space_open)
                        .takeIf { promptToOpenSpace },
                    onActionPerformed = { onOpenSpace(it) }
                )
                performRefresh()
            } ?: run {
                val errorString = appContext.getString(R.string.error_generic)
                popupModel.showSnackbar(errorString)
            }
        }
    }

    /** Toggles between making the [Space] public or private */
    fun setSpacePublicACL(spaceID: String, newIsPublicValue: Boolean) {
        coroutineScope.launch(dispatchers.io) {
            val space = dao.getSpaceById(spaceID) ?: return@launch

            stateFlow.value = State.UPDATING_DB_AFTER_MUTATION

            val mutation = when (newIsPublicValue) {
                true -> AddSpacePublicACLMutation(
                    input = AddSpacePublicACLInput(
                        id = Optional.presentIfNotNull(space.id)
                    )
                )

                false -> DeleteSpacePublicACLMutation(
                    input = DeleteSpacePublicACLInput(
                        id = Optional.presentIfNotNull(space.id)
                    )
                )
            }
            val response = authenticatedApolloWrapper.performMutation(
                mutation,
                userMustBeLoggedIn = true
            ).response

            response?.data?.let {
                dao.upsert(space.copy(isPublic = newIsPublicValue))
            } ?: run {
                val errorString = appContext.getString(R.string.error_generic)
                popupModel.showSnackbar(errorString)
            }
            stateFlow.value = State.READY
        }
    }

    suspend fun getEditSpaceInfo(mode: SpaceEditMode, id: String?): EditSpaceInfo? =
        id?.let { editingId ->
            when (mode) {
                SpaceEditMode.EDITING_SPACE, SpaceEditMode.ADDING_SPACE_ITEM -> EditSpaceInfo(
                    space = dao.getSpaceById(editingId),
                    spaceItem = null,
                    editMode = mode
                )

                SpaceEditMode.EDITING_SPACE_ITEM -> EditSpaceInfo(
                    space = null,
                    spaceItem = dao.getSpaceItemById(editingId),
                    editMode = mode
                )
            }
        }

    fun doesSpaceExist(spaceId: String?): Boolean {
        spaceId ?: return false
        return allSpacesFlow.value.any { it.id == spaceId }
    }

    private suspend fun saveBitmap(
        directory: File,
        dispatchers: Dispatchers,
        id: String,
        bitmapString: String?
    ) = withContext(dispatchers.io) {
        // Don't bother writing the file out if it already exists.
        val file = File(directory, id)
        try {
            if (file.exists()) return@withContext file
        } catch (throwable: SecurityException) {
            Timber.e(
                t = throwable,
                message = "Failed to check if bitmap exists: ${file.absolutePath}"
            )
            return@withContext null
        }

        val bitmap = bitmapString?.toBitmap() ?: return@withContext null
        val scaledBitmap = bitmap.scaleDownMaintainingAspectRatio(MAX_THUMBNAIL_SIZE)
        val bitmapBytes = scaledBitmap.toByteArray()
        return@withContext BitmapIO.saveBitmap(directory, file, ::FileOutputStream) {
            it.write(bitmapBytes)
        }
    }

    private fun String.toBitmap(): Bitmap? {
        val encoded = this
            .takeIf { it.startsWith(BitmapIO.DATA_URI_PREFIX) }
            ?.drop(BitmapIO.DATA_URI_PREFIX.length)
            ?: return null

        return try {
            val byteArray = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
