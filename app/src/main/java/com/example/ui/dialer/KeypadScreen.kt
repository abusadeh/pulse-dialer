package com.example.ui.dialer

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactItem
import com.example.data.model.SimInfo
import com.example.data.repository.SpeedDialEntry
import com.example.ui.theme.CallGreen

data class KeypadKey(
    val digit: Char,
    val letters: String,
)

private val KEYPAD_KEYS = listOf(
    listOf(KeypadKey('1', ""), KeypadKey('2', "ABC"), KeypadKey('3', "DEF")),
    listOf(KeypadKey('4', "GHI"), KeypadKey('5', "JKL"), KeypadKey('6', "MNO")),
    listOf(KeypadKey('7', "PQRS"), KeypadKey('8', "TUV"), KeypadKey('9', "WXYZ")),
    listOf(KeypadKey('*', ""), KeypadKey('0', "+"), KeypadKey('#', "")),
)

@Composable
fun KeypadScreen(
    dialedNumber: String,
    hapticsEnabled: Boolean,
    onAppendDigit: (Char) -> Unit,
    onDeleteDigit: () -> Unit,
    onClearDigits: () -> Unit,
    onSelectSim: (SimInfo) -> Unit,
    onPlaceCall: (String) -> Unit,
    onAddContact: (String) -> Unit,
    onConfigureSpeedDial: (Int) -> Unit,
    modifier: Modifier = Modifier,
    t9Matches: List<ContactItem> = emptyList(),
    availableSims: List<SimInfo> = emptyList(),
    selectedSim: SimInfo? = null,
    speedDialMap: Map<Int, SpeedDialEntry> = emptyMap(),
) {
    val view = LocalView.current
    var simMenuExpanded by remember { mutableStateOf(value = false) }

    fun triggerHaptic(strong: Boolean = false) {
        if (hapticsEnabled) {
            try {
                val flag = if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
                view.performHapticFeedback(flag)
            } catch (_: Exception) {}
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableHeight = this.maxHeight
        val buttonSize = when {
            availableHeight < 540.dp -> 54.dp
            availableHeight < 640.dp -> 62.dp
            else -> 70.dp
        }
        val callButtonSize = when {
            availableHeight < 540.dp -> 58.dp
            availableHeight < 640.dp -> 64.dp
            else -> 72.dp
        }
        val rowPadding = if (availableHeight < 540.dp) 2.dp else 4.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP: Dialed Number & Quick Match Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // T9 Matches Carousel (if any)
                AnimatedVisibility(visible = t9Matches.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "Suggested Contacts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(t9Matches, key = { it.id }) { contact ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable {
                                        triggerHaptic()
                                        val phone = contact.phoneNumbers.firstOrNull() ?: dialedNumber
                                        onPlaceCall(phone)
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = contact.initial.toString(),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = contact.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = contact.phoneNumbers.firstOrNull() ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dialed Number Display Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = dialedNumber.ifEmpty { "" },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (dialedNumber.length > 12) 26.sp else 34.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (dialedNumber.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                triggerHaptic()
                                onDeleteDigit()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("dialer_backspace_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick "Add to contacts" chip when number is typed
                AnimatedVisibility(visible = dialedNumber.isNotBlank()) {
                    AssistChip(
                        onClick = {
                            triggerHaptic()
                            onAddContact(dialedNumber)
                        },
                        label = { Text("Add to Contacts") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            // BOTTOM: Keypad Grid & Call Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Keypad 4 rows
                for (row in KEYPAD_KEYS) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = rowPadding),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            val digitInt = key.digit.digitToIntOrNull()
                            val speedEntry = digitInt?.let { speedDialMap[it] }

                            KeypadButton(
                                key = key,
                                size = buttonSize,
                                onClick = {
                                    triggerHaptic()
                                    onAppendDigit(key.digit)
                                },
                                onLongClick = {
                                    when (key.digit) {
                                        '0' -> {
                                            triggerHaptic(strong = true)
                                            onAppendDigit('+')
                                        }
                                        '1' -> {
                                            triggerHaptic(strong = true)
                                            onPlaceCall("voicemail:")
                                        }
                                        in '2'..'9' -> {
                                            triggerHaptic(strong = true)
                                            if (speedEntry?.number?.isNotEmpty() == true) {
                                                onPlaceCall(speedEntry.number)
                                            } else {
                                                onConfigureSpeedDial(key.digit.digitToInt())
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Multi-SIM Selector (if available)
                if (availableSims.size > 1) {
                    Box {
                        AssistChip(
                            onClick = { simMenuExpanded = true },
                            label = {
                                Text(
                                    text = selectedSim?.let { "SIM ${it.slotIndex + 1}: ${it.displayName}" } ?: "Select SIM",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = "SIM Selector",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )

                        DropdownMenu(
                            expanded = simMenuExpanded,
                            onDismissRequest = { simMenuExpanded = false }
                        ) {
                            availableSims.forEach { sim ->
                                DropdownMenuItem(
                                    text = { Text("SIM ${sim.slotIndex + 1} (${sim.carrierName.ifEmpty { sim.displayName }})") },
                                    onClick = {
                                        onSelectSim(sim)
                                        simMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Green Call Button and Clear
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left spacer / clear all
                    if (dialedNumber.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                triggerHaptic(strong = true)
                                onClearDigits()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // Call Action Button
                    Box(
                        modifier = Modifier
                            .size(callButtonSize)
                            .clip(CircleShape)
                            .background(CallGreen)
                            .clickable {
                                triggerHaptic(strong = true)
                                onPlaceCall(dialedNumber)
                            }
                            .testTag("dialer_call_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Place Call",
                            tint = Color.White,
                            modifier = Modifier.size(callButtonSize * 0.48f)
                        )
                    }

                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadButton(
    key: KeypadKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 68.dp,
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .then(clickModifier)
            .testTag("keypad_button_${key.digit}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.digit.toString(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (size < 60.dp) 20.sp else 24.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (size < 60.dp) 9.sp else 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
