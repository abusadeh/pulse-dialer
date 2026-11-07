package com.example.ui.call

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActiveCallState
import com.example.telecom.CallManager
import com.example.ui.theme.PulseDialerTheme
import kotlinx.coroutines.delay

class OngoingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn screen on and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            PulseDialerTheme(darkTheme = true) {
                val callState by CallManager.callStateFlow.collectAsStateWithLifecycle()

                // Auto finish activity when call finishes
                LaunchedEffect(callState) {
                    if (callState == null || callState?.state == ActiveCallState.DISCONNECTED) {
                        delay(1200)
                        finishAndRemoveTask()
                    }
                }

                CallScreen(
                    callState = callState,
                    onFinish = {
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }
}
