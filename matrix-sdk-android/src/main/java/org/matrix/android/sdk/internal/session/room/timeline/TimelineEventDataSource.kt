/*
 * Copyright (c) 2022 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.android.sdk.internal.session.room.timeline

import androidx.lifecycle.LiveData
import com.zhuinden.monarchy.Monarchy
import io.realm.Sort
import org.matrix.android.sdk.api.session.events.model.getRelationContent
import org.matrix.android.sdk.api.session.events.model.isImageMessage
import org.matrix.android.sdk.api.session.events.model.isVideoMessage
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.util.Optional
import org.matrix.android.sdk.internal.database.RealmSessionProvider
import org.matrix.android.sdk.internal.database.mapper.TimelineEventMapper
import org.matrix.android.sdk.internal.database.model.TimelineEventEntity
import org.matrix.android.sdk.internal.database.model.TimelineEventEntityFields
import org.matrix.android.sdk.internal.database.query.where
import org.matrix.android.sdk.internal.database.query.whereRoomId
import org.matrix.android.sdk.internal.di.SessionDatabase
import org.matrix.android.sdk.internal.task.TaskExecutor
import javax.inject.Inject

internal class TimelineEventDataSource @Inject constructor(
        private val realmSessionProvider: RealmSessionProvider,
        private val timelineEventMapper: TimelineEventMapper,
        private val taskExecutor: TaskExecutor,
        @SessionDatabase private val monarchy: Monarchy
) {

    fun getTimelineEvent(roomId: String, eventId: String): TimelineEvent? {
        return realmSessionProvider.withRealm { realm ->
            TimelineEventEntity.where(realm, roomId = roomId, eventId = eventId).findFirst()?.let {
                timelineEventMapper.map(it)
            }
        }
    }

    fun getTimelineEventLive(roomId: String, eventId: String): LiveData<Optional<TimelineEvent>> {
        return LiveTimelineEvent(monarchy, taskExecutor.executorScope, timelineEventMapper, roomId, eventId)
    }

    fun getAttachmentMessages(roomId: String,filters:(TimelineEvent)->Boolean = {it.root.isImageMessage() || it.root.isVideoMessage()}): List<TimelineEvent> {
        // TODO pretty bad query.. maybe we should denormalize clear type in base?
        return realmSessionProvider.withRealm { realm ->
            TimelineEventEntity.whereRoomId(realm, roomId)
                    .sort(TimelineEventEntityFields.ROOT.ORIGIN_SERVER_TS, Sort.ASCENDING)
                    .distinct(TimelineEventEntityFields.EVENT_ID)
                    .findAll()
                    ?.mapNotNull { timelineEventMapper.map(it).takeIf { timelineEvent -> filters.invoke(timelineEvent) } }
                    .orEmpty()
        }
    }

    fun getTimelineEventsRelatedTo(roomId: String, eventType: String, eventId: String): List<TimelineEvent> {
        // TODO Remove this trick and call relations API
        // see https://spec.matrix.org/latest/client-server-api/#get_matrixclientv1roomsroomidrelationseventidreltypeeventtype
        return realmSessionProvider.withRealm { realm ->
            TimelineEventEntity.whereRoomId(realm, roomId)
                    .sort(TimelineEventEntityFields.ROOT.ORIGIN_SERVER_TS, Sort.ASCENDING)
                    .distinct(TimelineEventEntityFields.EVENT_ID)
                    .findAll()
                    .mapNotNull {
                        timelineEventMapper.map(it)
                                .takeIf {
                                    val isEventRelatedTo = it.root.getRelationContent()?.takeIf { it.type == eventType && it.eventId == eventId } != null
                                    val isContentRelatedTo = it.root.getClearContent()?.toModel<MessageContent>()
                                            ?.relatesTo?.takeIf { it.type == eventType && it.eventId == eventId } != null
                                    isEventRelatedTo || isContentRelatedTo
                                }
                    }
        }
    }
}
