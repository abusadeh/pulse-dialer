package com.example.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.example.data.repository.BlockedNumbersRepository
import com.example.ui.call.OngoingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PulseInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Calls we rejected as blocked; they never reach CallManager or the UI. */
    private val blockedCalls = mutableSetOf<Call>()

    /** Incoming calls whose block-list check is still running. */
    private val pendingCalls = mutableSetOf<Call>()

    override fun onCreate() {
        super.onCreate()
        CallManager.registerInCallService(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val handle = call.details?.handle?.schemeSpecificPart.orEmpty()

        if (call.compatState != Call.STATE_RINGING || handle.isBlank()) {
            showCall(call, handle)
            return
        }

        // Fallback for when PulseCallScreeningService did not run. The block list is read off
        // the main thread; the call is not shown until the check is done.
        pendingCalls.add(call)
        serviceScope.launch {
            val blocked = withContext(Dispatchers.IO) {
                BlockedNumbersRepository.getInstance(applicationContext).isNumberBlocked(handle)
            }
            if (!pendingCalls.remove(call)) return@launch // Caller hung up meanwhile.
            if (blocked) {
                blockedCalls.add(call)
                try {
                    call.reject(false, null)
                } catch (_: Exception) {
                    call.disconnect()
                }
            } else {
                showCall(call, handle)
            }
        }
    }

    private fun showCall(call: Call, handle: String) {
        CallManager.registerInCallService(this)
        CallManager.onCallAdded(call, applicationContext)

        if (call.compatState == Call.STATE_RINGING) {
            // Heads-up / full-screen notification. CallManager updates the name once the contact
            // lookup finishes.
            CallNotificationHelper.showIncomingCallNotification(
                context = applicationContext,
                callerName = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                    ?: handle.ifBlank { "Unknown" },
                callerNumber = handle
            )
        }

        val intent = Intent(this, OngoingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        // Blocked or not-yet-shown calls must not touch the state or notifications of a real call.
        if (blockedCalls.remove(call) || pendingCalls.remove(call)) return
        // CallManager clears the notifications once the last call has ended.
        CallManager.onCallRemoved(call)
    }

    // Still needed below API 34, where onCallEndpointChanged is unavailable.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        blockedCalls.clear()
        pendingCalls.clear()
        CallManager.unregisterInCallService()
        CallNotificationHelper.cancelAll(this)
    }
}
