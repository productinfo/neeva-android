// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.apollo

import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query

/** Manages calls made via Apollo to the Neeva backend. */
interface ApolloWrapper {
    suspend fun <D : Query.Data> performQuery(
        query: Query<D>,
        userMustBeLoggedIn: Boolean
    ): ApolloResponseSummary<D>

    suspend fun <D : Mutation.Data> performMutation(
        mutation: Mutation<D>,
        userMustBeLoggedIn: Boolean
    ): ApolloResponseSummary<D>
}
data class ApolloResponseSummary <D : Operation.Data>(
    val response: ApolloResponse<D>?,
    val exception: Exception?
)
