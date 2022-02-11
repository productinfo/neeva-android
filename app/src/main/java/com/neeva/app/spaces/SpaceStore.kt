package com.neeva.app.spaces

import android.net.Uri
import android.util.Log
import com.apollographql.apollo3.api.Optional
import com.neeva.app.AddToSpaceMutation
import com.neeva.app.ApolloWrapper
import com.neeva.app.DeleteSpaceResultByURLMutation
import com.neeva.app.GetSpacesDataQuery
import com.neeva.app.ListSpacesQuery
import com.neeva.app.storage.NeevaUser
import com.neeva.app.type.AddSpaceResultByURLInput
import com.neeva.app.type.DeleteSpaceResultByURLInput
import com.neeva.app.type.SpaceACLLevel
import kotlinx.coroutines.flow.MutableStateFlow

/** Manages interactions with the user's Spaces. */
class SpaceStore(val apolloWrapper: ApolloWrapper, val neevaUser: NeevaUser) {
    companion object {
        private val TAG = SpaceStore::class.simpleName
    }

    enum class State {
        READY,
        REFRESHING,
        FAILED
    }

    val allSpacesFlow = MutableStateFlow<List<Space>>(emptyList())
    val editableSpacesFlow = MutableStateFlow<List<Space>>(emptyList())
    val stateFlow = MutableStateFlow(State.READY)

    /** Mapping of URIs to [Space]s that contain them. */
    private val urlToSpacesMap = mutableMapOf<Uri, MutableList<Space>>()

    fun spaceStoreContainsUrl(url: Uri): Boolean = urlToSpacesMap[url]?.isNotEmpty() ?: false

    suspend fun refresh() {
        if (stateFlow.value == State.REFRESHING) return
        stateFlow.value = State.REFRESHING

        val succeeded = performRefresh()
        stateFlow.value = if (succeeded) {
            State.READY
        } else {
            State.FAILED
        }
    }

    private suspend fun performRefresh(): Boolean {
        val response = apolloWrapper.performQuery(ListSpacesQuery()) ?: return false

        // If there are no spaces to process, but the response was fine, just indicate success.
        val listSpaces = response.data?.listSpaces ?: return true
        val oldSpaceMap = allSpacesFlow.value.associateBy { it.id }

        // Clear to avoid holding stale data. Will be rebuilt below.
        urlToSpacesMap.clear()

        // Fetch all the of the user's Spaces.
        val spacesToFetch = mutableListOf<Space>()
        val spaceList = listSpaces.space
            .map { listSpacesQuery ->
                val newSpace = listSpacesQuery.toSpace(neevaUser.data.id) ?: return@map null

                val oldSpace = oldSpaceMap[newSpace.id]
                if (oldSpace != null &&
                    oldSpace.lastModifiedTs == newSpace.lastModifiedTs &&
                    oldSpace.contentData != null &&
                    oldSpace.contentURLs != null
                ) {
                    // The Space hasn't been updated since the last fetch.
                    // At this point, newSpace doesn't contain any URLs or data so we have to reuse
                    // the data that was previously fetched.
                    onUpdateSpaceURLs(newSpace, oldSpace.contentURLs!!, oldSpace.contentData!!)
                } else {
                    spacesToFetch.add(newSpace)
                }

                newSpace
            }
            .filterNotNull()

        allSpacesFlow.value = spaceList
        editableSpacesFlow.value = spaceList
            .filter { it.userACL == SpaceACLLevel.Owner || it.userACL == SpaceACLLevel.Edit }

        // Get updated data for any Spaces that have changed since the last fetch.
        val spacesDataResponse = apolloWrapper.performQuery(
            GetSpacesDataQuery(Optional.presentIfNotNull(spacesToFetch.map { it.id }))
        ) ?: return false

        spacesDataResponse.data?.getSpace?.space?.forEach { spaceQuery ->
            val entityQueries = spaceQuery.space?.entities ?: return@forEach

            val entities = entityQueries
                .map { entityQuery ->
                    val url = Uri.parse(entityQuery.spaceEntity?.url) ?: return@map null
                    SpaceEntityData(
                        url,
                        title = entityQuery.spaceEntity?.title,
                        snippet = entityQuery.spaceEntity?.snippet,
                        thumbnail = entityQuery.spaceEntity?.thumbnail
                    )
                }
                .filterNotNull()

            val contentUrls = entities.map { it.url }.toSet()
            spacesToFetch
                .firstOrNull { space -> space.id == spaceQuery.pageMetadata?.pageID }
                ?.let { space -> onUpdateSpaceURLs(space, contentUrls, entities) }
        }

        return true
    }

    suspend fun addOrRemoveFromSpace(
        spaceID: String,
        url: Uri,
        title: String,
        description: String? = null
    ): Boolean {
        val space = allSpacesFlow.value.find { it.id == spaceID } ?: return false

        return if (space.contentURLs?.contains(url) == true) {
            removeFromSpace(space.id, url)
        } else {
            addToSpace(space.id, url, title, description)
        }
    }

    suspend fun addToSpace(
        spaceID: String,
        url: Uri,
        title: String,
        description: String? = null
    ): Boolean {
        val response = apolloWrapper.performMutation(
            AddToSpaceMutation(
                input = AddSpaceResultByURLInput(
                    spaceID = spaceID,
                    url = url.toString(),
                    title = title,
                    data = description?.let { Optional.presentIfNotNull(it) }
                        ?: Optional.Absent,
                    mediaType = Optional.presentIfNotNull("text/plain")
                )
            )
        )

        return response?.data?.entityId?.let {
            // TODO Add a toast here: https://github.com/neevaco/neeva-android/issues/287
            Log.i(TAG, "Added item to space with id=$it")
            refresh()
            true
        } ?: false
    }

    suspend fun removeFromSpace(spaceID: String, uri: Uri): Boolean {
        val response = apolloWrapper.performMutation(
            DeleteSpaceResultByURLMutation(
                input = DeleteSpaceResultByURLInput(
                    spaceID = spaceID,
                    url = uri.toString(),
                )
            )
        )

        return response?.data?.deleteSpaceResultByURL?.let {
            // TODO Add a toast here: https://github.com/neevaco/neeva-android/issues/287
            // TODO(yusuf): The result isn't checked here.
            Log.i(TAG, "Deleted item from space")
            refresh()
            true
        } ?: false
    }

    private fun onUpdateSpaceURLs(space: Space, urls: Set<Uri>, data: List<SpaceEntityData>) {
        space.contentURLs = urls
        space.contentData = data
        urls.forEach {
            val spaces = urlToSpacesMap[it] ?: mutableListOf()
            spaces.add(space)
            urlToSpacesMap[it] = spaces
        }
    }
}