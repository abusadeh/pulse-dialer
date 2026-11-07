package com.example.data.model

enum class ActiveCallState {
    IDLE,
    RINGING,       // Incoming call
    DIALING,       // Outgoing call dialing
    CONNECTING,    // Outgoing call connecting
    ACTIVE,        // Connected & talking
    HOLDING,       // On hold
    DISCONNECTING,
    DISCONNECTED
}

data class CallStateInfo(
    val callId: String,
    val state: ActiveCallState,
    val number: String,
    val displayName: String?,
    val photoUri: String?,
    val durationSeconds: Long = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val isIncoming: Boolean = false,
    val canHold: Boolean = true,
    val canMute: Boolean = true,
    val hasSecondaryCall: Boolean = false,
    val secondaryCallName: String? = null,
    val secondaryCallNumber: String? = null
)
