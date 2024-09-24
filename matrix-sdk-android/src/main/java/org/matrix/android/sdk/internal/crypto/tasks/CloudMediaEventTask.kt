/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.matrix.android.sdk.internal.crypto.tasks

import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.util.MimeTypes
import org.matrix.android.sdk.api.util.toMimeType
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.session.room.RoomAPI
import org.matrix.android.sdk.internal.task.Task
import javax.inject.Inject

internal interface CloudMediaEventTask : Task<CloudMediaEventTask.Params, String> {
    data class Params(
        val event: Event,
        val token: String,
    )
}

internal class DefaultCloudMediaEventTask @Inject constructor(
    private val roomAPI: RoomAPI,
    private val globalErrorReceiver: GlobalErrorReceiver,
) : CloudMediaEventTask {

    override suspend fun execute(params: CloudMediaEventTask.Params): String {
        val response = executeRequest(globalErrorReceiver) {
            roomAPI.uploadNas(
                content = params.event.content!!.toMutableMap().apply {
                    put("token", params.token)
                    put("room_id", params.event.roomId!!)
                    put("txn_id", params.event.eventId!!)
                    getOrPut("info") {
                        mapOf(
                            Pair("mimetype", (params.event.content["path"]?.let {
                                (it as String).toMimeType()
                            }) ?: MimeTypes.Any),
//                            Pair("size", 0)
                        )
                    }
                },
            )
        }
        return response.eventId
    }
}
