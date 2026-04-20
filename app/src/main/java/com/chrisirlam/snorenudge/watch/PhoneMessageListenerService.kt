package com.chrisirlam.snorenudge.watch

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "PhoneMessageListener"

/**
 * Receives messages sent from the watch back to the phone (e.g. watch dismissed nudge).
 * Currently a no-op placeholder — extend to handle watch-initiated events.
 */
class PhoneMessageListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Message received from watch: path=${event.path}")
        // Future: handle watch-originated messages (e.g. user dismissed nudge on watch)
    }
}
