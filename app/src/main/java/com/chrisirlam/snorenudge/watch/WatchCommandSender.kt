package com.chrisirlam.snorenudge.watch

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

private const val TAG = "WatchCommandSender"

/** Message path constants shared between phone and watch. */
object WearPaths {
    const val VIBRATE = "/snore/vibrate"
    const val STOP_VIBRATE = "/snore/stop_vibrate"
    const val TEST_VIBRATE = "/snore/test_vibrate"
    const val STATUS_UPDATE = "/snore/status"
}

/**
 * Sends one-shot commands to all connected watch nodes via the Wearable Message API.
 *
 * The Wearable Message API is lower-latency than the Data Layer for fire-and-forget
 * commands, making it the right choice for a time-sensitive nudge.
 */
class WatchCommandSender(private val context: Context) {

    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }

    /**
     * Sends a vibrate command to all reachable watch nodes.
     * @param strong True for a strong repeating pattern, false for a shorter pulse.
     * @return Number of nodes the message was successfully sent to.
     */
    suspend fun sendVibrateCommand(strong: Boolean = true): Int {
        val payload = if (strong) "strong" else "medium"
        return sendToAllNodes(WearPaths.VIBRATE, payload.toByteArray())
    }

    suspend fun sendStopVibrateCommand(): Int =
        sendToAllNodes(WearPaths.STOP_VIBRATE, ByteArray(0))

    suspend fun sendTestVibrateCommand(): Int =
        sendToAllNodes(WearPaths.TEST_VIBRATE, ByteArray(0))

    /** Returns the number of reachable watch nodes (0 = watch not connected). */
    suspend fun getConnectedNodeCount(): Int {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.size
        } catch (e: Exception) {
            Log.w(TAG, "Could not query connected nodes: ${e.message}")
            0
        }
    }

    private suspend fun sendToAllNodes(path: String, payload: ByteArray): Int {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected watch nodes, cannot send $path")
                return 0
            }
            var sent = 0
            for (node in nodes) {
                try {
                    messageClient.sendMessage(node.id, path, payload).await()
                    Log.d(TAG, "Sent $path to ${node.displayName}")
                    sent++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send $path to ${node.displayName}: ${e.message}")
                }
            }
            sent
        } catch (e: Exception) {
            Log.e(TAG, "sendToAllNodes failed for $path: ${e.message}")
            0
        }
    }
}
