package com.example.telecom

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.example.data.model.ActiveCallState
import com.example.data.model.CallStateInfo
import com.example.data.repository.ContactsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Call state that works on every supported API level.
 * [Call.getState] is deprecated since API 31 in favour of [Call.Details.getState].
 */
internal val Call.compatState: Int
    get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        details.state
    } else {
        @Suppress("DEPRECATION")
        state
    }

/**
 * Tracks up to two Telecom calls and exposes the one shown on screen as [callStateFlow].
 *
 * [activeCall] is the "foreground" call: the one the call screen shows and that Answer,
 * Decline, Hang up, Hold, Mute and DTMF act on. A newly added call (an incoming waiting call or
 * a second outgoing call) always becomes the foreground call, and the previous one moves to
 * [secondaryCall]. When the foreground call ends, the other call takes its place.
 *
 * All methods must be called on the main thread (Telecom callbacks and UI both are).
 */
object CallManager {

    private data class CallerInfo(val number: String, val name: String, val photoUri: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerJob: Job? = null

    private var activeCall: Call? = null
    private var secondaryCall: Call? = null
    private var inCallServiceRef: WeakReference<InCallService>? = null
    private var appContext: Context? = null

    /** Caller ID resolved from contacts, per call. */
    private val callerInfo = mutableMapOf<Call, CallerInfo>()

    private val _callStateFlow = MutableStateFlow<CallStateInfo?>(null)
    val callStateFlow: StateFlow<CallStateInfo?> = _callStateFlow.asStateFlow()

    private var isMuted = false
    private var isSpeakerOn = false

    /** Last ongoing notification content, so it is only re-posted when something changes. */
    private var lastOngoingNotificationKey: String? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateCallState()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            updateCallState()
        }
    }

    fun registerInCallService(service: InCallService) {
        inCallServiceRef = WeakReference(service)
        appContext = service.applicationContext
    }

    fun unregisterInCallService() {
        inCallServiceRef = null
    }

    fun onCallAdded(call: Call, context: Context) {
        appContext = context.applicationContext
        if (call == activeCall || call == secondaryCall) return

        when {
            activeCall == null -> {
                activeCall = call
                stopDurationTimer()
                // Drop leftover state from a previous call (the 1.2s "disconnected" screen) so
                // its number and name are not reused for this one.
                _callStateFlow.value = null
                lastOngoingNotificationKey = null
            }
            secondaryCall == null -> {
                // Bring the new call to the front so its Answer/Decline (or dialing) UI shows.
                secondaryCall = activeCall
                activeCall = call
            }
            else -> return // Only two calls are supported; leave extra calls to Telecom.
        }

        call.registerCallback(callCallback)
        updateCallState()
        resolveCallerInfo(call, context.applicationContext)
    }

    fun onCallRemoved(call: Call) {
        call.unregisterCallback(callCallback)
        callerInfo.remove(call)
        when (call) {
            activeCall -> {
                activeCall = secondaryCall
                secondaryCall = null
            }
            secondaryCall -> secondaryCall = null
            else -> return // Not a call we were tracking.
        }

        if (activeCall == null) {
            stopDurationTimer()
            appContext?.let { CallNotificationHelper.cancelAll(it) }
            lastOngoingNotificationKey = null
            _callStateFlow.value = _callStateFlow.value?.copy(state = ActiveCallState.DISCONNECTED)
            // Allow a brief "Call ended" screen before resetting.
            mainHandler.postDelayed({
                if (activeCall == null) _callStateFlow.value = null
            }, 1200)
        } else {
            updateCallState()
        }
    }

    fun onAudioStateChanged(audioState: CallAudioState) {
        isMuted = audioState.isMuted
        isSpeakerOn = audioState.route == CallAudioState.ROUTE_SPEAKER
        _callStateFlow.value = _callStateFlow.value?.copy(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn
        )
    }

    /** Answers the ringing call. Telecom puts the other call (if any) on hold. */
    fun answer() {
        val ringing = ringingCall() ?: return
        appContext?.let { CallNotificationHelper.cancelIncomingNotification(it) }
        bringToFront(ringing)
        ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
        updateCallState()
    }

    /** Declines the ringing call; the other call (if any) is untouched. */
    fun reject() {
        val ringing = ringingCall() ?: return
        appContext?.let { CallNotificationHelper.cancelIncomingNotification(it) }
        ringing.reject(false, null)
    }

    /** Hangs up the foreground call. A ringing foreground call is declined instead. */
    fun disconnect() {
        val call = activeCall ?: return
        if (call.compatState == Call.STATE_RINGING) {
            reject()
        } else {
            call.disconnect()
        }
    }

    fun setMuted(muted: Boolean) {
        try {
            inCallServiceRef?.get()?.setMuted(muted)
        } catch (_: Exception) {}
        isMuted = muted
        _callStateFlow.value = _callStateFlow.value?.copy(isMuted = muted)
    }

    fun setSpeaker(speaker: Boolean) {
        try {
            val route = if (speaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            // setAudioRoute is deprecated on API 34+, but CallEndpoint APIs are unavailable below it.
            @Suppress("DEPRECATION")
            inCallServiceRef?.get()?.setAudioRoute(route)
        } catch (_: Exception) {}
        isSpeakerOn = speaker
        _callStateFlow.value = _callStateFlow.value?.copy(isSpeakerOn = speaker)
    }

    fun toggleHold() {
        val call = activeCall ?: return
        when (call.compatState) {
            Call.STATE_HOLDING -> {
                // Resuming this call: hold the other one so only one call is live.
                secondaryCall?.takeIf { it.compatState == Call.STATE_ACTIVE }?.hold()
                call.unhold()
            }
            Call.STATE_ACTIVE -> call.hold()
        }
    }

    /** Puts the foreground call on hold and resumes the other one. */
    fun swapCalls() {
        val front = activeCall ?: return
        val back = secondaryCall ?: return
        // Swapping is meaningless while one of the calls is still ringing or dialing.
        if (front.compatState !in SWAPPABLE_STATES || back.compatState !in SWAPPABLE_STATES) return
        if (front.compatState == Call.STATE_ACTIVE) front.hold()
        back.unhold()
        activeCall = back
        secondaryCall = front
        updateCallState()
    }

    fun playDtmf(digit: Char) {
        activeCall?.playDtmfTone(digit)
    }

    fun stopDtmf() {
        activeCall?.stopDtmfTone()
    }

    private val SWAPPABLE_STATES = setOf(Call.STATE_ACTIVE, Call.STATE_HOLDING)

    private fun ringingCall(): Call? =
        listOfNotNull(activeCall, secondaryCall).firstOrNull { it.compatState == Call.STATE_RINGING }

    private fun bringToFront(call: Call) {
        if (call == secondaryCall) {
            secondaryCall = activeCall
            activeCall = call
        }
    }

    private fun rawNumber(call: Call): String? =
        call.details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }

    /** Looks up the caller in contacts off the main thread, then refreshes the UI. */
    private fun resolveCallerInfo(call: Call, context: Context) {
        val number = rawNumber(call) ?: return
        scope.launch {
            val contact = try {
                ContactsRepository(context).findContactByNumber(number) // runs on Dispatchers.IO
            } catch (_: Exception) {
                null
            }
            // The call may have ended while we were looking it up.
            if (call != activeCall && call != secondaryCall) return@launch

            val name = contact?.displayName
                ?: call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                ?: number
            callerInfo[call] = CallerInfo(number, name, contact?.photoUri)

            if (call.compatState == Call.STATE_RINGING && contact != null) {
                // Replace the notification's raw number with the contact name.
                CallNotificationHelper.showIncomingCallNotification(context, name, number)
            }
            updateCallState()
        }
    }

    private fun displayName(call: Call): String {
        callerInfo[call]?.let { return it.name }
        return call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: rawNumber(call)
            ?: "Unknown"
    }

    private fun updateCallState() {
        val call = activeCall ?: return
        val callState = call.compatState

        if (ringingCall() == null) {
            appContext?.let { CallNotificationHelper.cancelIncomingNotification(it) }
        }

        val state = when (callState) {
            Call.STATE_RINGING -> ActiveCallState.RINGING
            Call.STATE_DIALING, Call.STATE_SELECT_PHONE_ACCOUNT -> ActiveCallState.DIALING
            Call.STATE_CONNECTING -> ActiveCallState.CONNECTING
            Call.STATE_ACTIVE -> ActiveCallState.ACTIVE
            Call.STATE_HOLDING -> ActiveCallState.HOLDING
            Call.STATE_DISCONNECTING -> ActiveCallState.DISCONNECTING
            Call.STATE_DISCONNECTED -> ActiveCallState.DISCONNECTED
            else -> ActiveCallState.IDLE
        }
        if (connectTime(call) > 0L) startDurationTimer()

        val info = callerInfo[call]
        val number = info?.number ?: rawNumber(call) ?: "Unknown"
        val secCall = secondaryCall

        _callStateFlow.value = CallStateInfo(
            callId = call.toString(),
            state = state,
            number = number,
            displayName = displayName(call),
            photoUri = info?.photoUri,
            durationSeconds = connectedSeconds(call),
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isOnHold = callState == Call.STATE_HOLDING,
            isIncoming = callState == Call.STATE_RINGING,
            canHold = call.details?.can(Call.Details.CAPABILITY_HOLD) ?: true,
            hasSecondaryCall = secCall != null,
            secondaryCallName = secCall?.let { displayName(it) },
            secondaryCallNumber = secCall?.let { rawNumber(it) }
        )

        refreshOngoingNotification(call, state)
    }

    /** Shows (or updates) the ongoing-call notification while a call is dialing or connected. */
    private fun refreshOngoingNotification(call: Call, state: ActiveCallState) {
        val context = appContext ?: return
        val showable = state == ActiveCallState.DIALING ||
            state == ActiveCallState.CONNECTING ||
            state == ActiveCallState.ACTIVE ||
            state == ActiveCallState.HOLDING
        // While the foreground call is ringing, the incoming notification covers it; keep the
        // ongoing one for the other call as it is.
        if (!showable) return

        val name = displayName(call)
        val connectedAt = connectTime(call)
        val key = "$name|$state|$connectedAt"
        if (key == lastOngoingNotificationKey) return
        lastOngoingNotificationKey = key

        CallNotificationHelper.showOngoingCallNotification(
            context = context,
            callerName = name,
            connectTimeMillis = connectedAt,
            statusText = when (state) {
                ActiveCallState.HOLDING -> "On hold"
                ActiveCallState.ACTIVE -> "Call in progress"
                else -> "Calling…"
            }
        )
    }

    private fun startDurationTimer() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val call = activeCall ?: continue
                _callStateFlow.value = _callStateFlow.value?.copy(durationSeconds = connectedSeconds(call))
            }
        }
    }

    private fun connectTime(call: Call): Long = call.details?.connectTimeMillis ?: 0L

    /** Seconds since Telecom reports this call connected (0 while ringing or dialing). */
    private fun connectedSeconds(call: Call): Long {
        val connectedAt = connectTime(call)
        if (connectedAt <= 0L) return 0L
        return ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0L)
    }

    private fun stopDurationTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}
