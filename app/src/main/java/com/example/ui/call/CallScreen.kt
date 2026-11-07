package com.example.ui.call

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ActiveCallState
import com.example.data.model.CallStateInfo
import com.example.data.repository.AutoRecordMode
import com.example.data.repository.RecordCallType
import com.example.data.repository.RecordingsRepository
import com.example.data.repository.SettingsRepository
import com.example.telecom.CallManager
import com.example.telecom.CallRecordingEngine
import com.example.ui.theme.CallGreen
import com.example.ui.theme.CallOrange
import com.example.ui.theme.CallRed

@Composable
fun CallScreen(
    callState: CallStateInfo?,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val userSettings by settingsRepository.settingsFlow.collectAsStateWithLifecycle()

    var showDtmfKeypad by remember { mutableStateOf(false) }

    if (callState == null || callState.state == ActiveCallState.IDLE) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No active call",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0B132B),
            Color(0xFF1C2541),
            Color(0xFF0F172A)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Caller Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {

                // Pulse Animation for Ringing State
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_scale"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(130.dp)
                        .scale(if (callState.state == ActiveCallState.RINGING) pulseScale else 1f)
                ) {
                    if (callState.state == ActiveCallState.RINGING) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(CallGreen.copy(alpha = 0.2f))
                        )
                    }

                    if (!callState.photoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = callState.photoUri,
                            contentDescription = "Caller Photo",
                            modifier = Modifier
                                .size(105.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(105.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(60.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = callState.displayName ?: callState.number,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                if (callState.displayName != null && callState.displayName != callState.number) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = callState.number,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Call State Status badge
                val statusText = when (callState.state) {
                    ActiveCallState.RINGING -> "Incoming call…"
                    ActiveCallState.DIALING -> "Calling…"
                    ActiveCallState.CONNECTING -> "Connecting…"
                    ActiveCallState.HOLDING -> "On hold"
                    ActiveCallState.ACTIVE -> formatCallDuration(callState.durationSeconds)
                    ActiveCallState.DISCONNECTING -> "Disconnecting…"
                    ActiveCallState.DISCONNECTED -> "Call ended"
                    ActiveCallState.IDLE -> ""
                }

                Surface(
                    color = when (callState.state) {
                        ActiveCallState.HOLDING -> CallOrange.copy(alpha = 0.3f)
                        ActiveCallState.RINGING -> CallGreen.copy(alpha = 0.3f)
                        else -> Color.White.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = when (callState.state) {
                            ActiveCallState.HOLDING -> CallOrange
                            ActiveCallState.RINGING -> CallGreen
                            else -> Color.White.copy(alpha = 0.9f)
                        }
                    )
                }

                // Call waiting banner if secondary call exists
                if (callState.hasSecondaryCall) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    // The banner shows the *other* call: the one in progress while
                                    // a new call rings, otherwise the one on hold.
                                    text = if (callState.state == ActiveCallState.RINGING) "Current call" else "On hold",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = callState.secondaryCallName ?: "Other Call",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = { CallManager.swapCalls() },
                                // Can't swap to/from a call that is still ringing or dialing.
                                enabled = callState.state == ActiveCallState.ACTIVE ||
                                    callState.state == ActiveCallState.HOLDING,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapCalls,
                                    contentDescription = "Swap calls",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Middle Section: DTMF Keypad or In-Call Controls
            if (showDtmfKeypad) {
                DtmfKeypadView(
                    onDigitPress = { digit -> CallManager.playDtmf(digit) },
                    onDigitRelease = { CallManager.stopDtmf() },
                    onClose = { showDtmfKeypad = false }
                )
            } else if (callState.state != ActiveCallState.RINGING) {
                // In-Call action buttons grid (2 rows of 3 buttons)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Row 1: Mute, Keypad, Speaker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallActionButton(
                            icon = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (callState.isMuted) "Unmute" else "Mute",
                            isActive = callState.isMuted,
                            onClick = { CallManager.setMuted(!callState.isMuted) },
                            testTag = "call_mute_button"
                        )

                        CallActionButton(
                            icon = Icons.Default.Dialpad,
                            label = "Keypad",
                            isActive = showDtmfKeypad,
                            onClick = { showDtmfKeypad = true },
                            testTag = "call_keypad_button"
                        )

                        CallActionButton(
                            icon = if (callState.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            label = if (callState.isSpeakerOn) "Speaker" else "Earpiece",
                            isActive = callState.isSpeakerOn,
                            onClick = { CallManager.setSpeaker(!callState.isSpeakerOn) },
                            testTag = "call_speaker_button"
                        )
                    }

                    // Row 2: Hold, Record, Swap / Add
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallActionButton(
                            icon = if (callState.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                            label = if (callState.isOnHold) "Resume" else "Hold",
                            isActive = callState.isOnHold,
                            onClick = { CallManager.toggleHold() },
                            testTag = "call_hold_button"
                        )



                        if (callState.hasSecondaryCall) {
                            CallActionButton(
                                icon = Icons.Default.SwapCalls,
                                label = "Swap",
                                isActive = false,
                                onClick = { CallManager.swapCalls() },
                                testTag = "call_swap_button"
                            )
                        }
                    }
                }
            }

            // Bottom Section: Answer / Decline or Hang Up
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (callState.state == ActiveCallState.RINGING) {
                    // Incoming call: Answer (Green) and Decline (Red)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(CallRed)
                                    .clickable { CallManager.reject() }
                                    .testTag("call_reject_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Decline",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(CallGreen)
                                    .clickable { CallManager.answer() }
                                    .testTag("call_answer_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Answer",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Answer",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                } else {
                    // Active / Dialing / Holding: Red Hangup button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CallRed)
                                .clickable {
                                    // Don't close the screen here: if another call is on hold it
                                    // takes over the screen. OngoingCallActivity finishes itself
                                    // once the last call has ended.
                                    CallManager.disconnect()
                                }
                                .testTag("call_end_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End call",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "End call",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color.White,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive && activeColor == Color.White) Color(0xFF0F172A) else if (isActive) Color.White else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun DtmfKeypadView(
    onDigitPress: (Char) -> Unit,
    onDigitRelease: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1E293B).copy(alpha = 0.95f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Keypad",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Hide",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val rows = listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9'),
                listOf('*', '0', '#')
            )

            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (digit in row) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable {
                                    onDigitPress(digit)
                                    onDigitRelease()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit.toString(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

private fun formatCallDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
}
