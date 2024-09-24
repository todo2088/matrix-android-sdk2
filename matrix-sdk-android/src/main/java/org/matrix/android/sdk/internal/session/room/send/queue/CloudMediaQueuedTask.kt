package org.matrix.android.sdk.internal.session.room.send.queue

import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.room.send.SendState
import org.matrix.android.sdk.internal.crypto.tasks.CloudMediaEventTask
import org.matrix.android.sdk.internal.session.room.send.CancelSendTracker
import org.matrix.android.sdk.internal.session.room.send.LocalEchoRepository

internal class CloudMediaQueuedTask(
    val event: Event,
    val token: String,
    private val cloudMediaEventTask: CloudMediaEventTask,
    val localEchoRepository: LocalEchoRepository,
    val cancelSendTracker: CancelSendTracker,
) : QueuedTask(queueIdentifier = event.roomId!!, taskIdentifier = event.eventId!!) {
    override suspend fun doExecute() {
        cloudMediaEventTask.execute(CloudMediaEventTask.Params(event, token))
    }

    override fun onTaskFailed() {
        localEchoRepository.updateSendState(event.eventId!!, event.roomId, SendState.UNDELIVERED)
    }

    override fun isCancelled(): Boolean {
        return super.isCancelled() || cancelSendTracker.isCancelRequestedFor(
            event.eventId,
            event.roomId
        )
    }
}
