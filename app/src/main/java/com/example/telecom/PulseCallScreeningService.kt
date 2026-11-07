package com.example.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.example.data.repository.BlockedNumbersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Rejects calls from blocked numbers before the phone rings.
 *
 * Telecom binds this service for the default dialer app (Android 7+) and asks it about every
 * incoming call before ringing starts. Blocked calls are rejected silently but still appear in
 * the call log as blocked. The block list is read off the main thread; Telecom allows the
 * response to arrive asynchronously (within a few seconds).
 */
class PulseCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val isIncoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()

        if (!isIncoming || number.isBlank()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        scope.launch {
            val blocked = try {
                BlockedNumbersRepository.getInstance(applicationContext).isNumberBlocked(number)
            } catch (_: Exception) {
                false
            }
            val response = if (blocked) {
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .setSkipCallLog(false)
                    .build()
            } else {
                CallResponse.Builder().build()
            }
            respondToCall(callDetails, response)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
