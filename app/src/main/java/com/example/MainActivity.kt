package com.example

import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.ThemeMode
import com.example.telecom.CallNotificationHelper
import com.example.ui.MainViewModel
import com.example.ui.NavigationTab
import com.example.ui.PulseDialerApp
import com.example.ui.theme.PulseDialerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** True while the lock screen must be shown (only meaningful when App Lock is enabled). */
    private var isLocked by mutableStateOf(value = true)
    private var lockErrorMessage by mutableStateOf<String?>(null)

    /** Set while a system auth UI is showing, so its onStop/onResume don't re-lock or re-prompt. */
    private var authInProgress = false

    /** Show the prompt automatically only on a fresh start or return from background, so
     *  cancelling it leaves the user on the lock screen instead of re-prompting in a loop. */
    private var autoPromptPending = true

    // Fallback for Android 7–9: the system "confirm your PIN/pattern/password" screen.
    private val confirmCredentialLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            authInProgress = false
            if (result.resultCode == RESULT_OK) {
                onAuthSucceeded()
            } else {
                lockErrorMessage = "Verification cancelled"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Survive rotation without asking again; a fresh launch always starts locked.
        isLocked = savedInstanceState?.getBoolean(KEY_LOCKED, true) ?: true

        // Create notification channels for Telecom calls
        CallNotificationHelper.createNotificationChannels(this)

        // Handle incoming dial/tel intents
        handleDialIntent(intent)

        setContent {
            val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
            val isDark = when (userSettings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            PulseDialerTheme(
                darkTheme = isDark,
                accentIndex = userSettings.accentColorIndex,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (userSettings.appLockEnabled && isLocked) {
                        AppLockScreen(errorMessage = lockErrorMessage) {
                            requestUnlock()
                        }
                    } else {
                        PulseDialerApp(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDialIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
        if (autoPromptPending && isAppLockActive() && !authInProgress) {
            autoPromptPending = false
            requestUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        // Re-lock whenever the app leaves the screen, except for rotation or our own auth UI.
        if (!isChangingConfigurations && !authInProgress) {
            isLocked = true
            lockErrorMessage = null
            autoPromptPending = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, isLocked)
    }

    private fun isAppLockActive(): Boolean =
        viewModel.userSettings.value.appLockEnabled && isLocked

    private fun onAuthSucceeded() {
        authInProgress = false
        lockErrorMessage = null
        isLocked = false
    }

    /** Asks the system to verify the user with fingerprint/face or the device PIN/pattern/password. */
    private fun requestUnlock() {
        if (authInProgress) return
        lockErrorMessage = null

        val keyguard = getSystemService(KeyguardManager::class.java)
        if ((keyguard == null) || (!keyguard.isDeviceSecure)) {
            // Nothing to verify against; don't lock the user out of their phone app.
            Toast.makeText(
                this,
                "Set a screen lock in system settings for App Lock to protect Pulse Dialer",
                Toast.LENGTH_LONG,
            ).show()
            onAuthSucceeded()
            return
        }

        val title = "Unlock Pulse Dialer"
        val description = "Verify it's you to access calls and contacts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val builder = BiometricPrompt.Builder(this)
                .setTitle(title)
                .setSubtitle(description)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }

            authInProgress = true
            builder.build().authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onAuthSucceeded()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        authInProgress = false
                        lockErrorMessage = errString.toString()
                    }
                    // onAuthenticationFailed (one bad fingerprint) is retried inside the prompt.
                }
            )
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent(title, description)
            if (intent == null) {
                onAuthSucceeded()
                return
            }
            authInProgress = true
            confirmCredentialLauncher.launch(intent)
        }
    }

    private fun handleDialIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data: Uri? = intent.data

        if ((action == Intent.ACTION_DIAL) || (action == Intent.ACTION_VIEW)) {
            val number = data?.schemeSpecificPart
                ?: data?.toString()?.removePrefix("tel:")
                ?: intent.getStringExtra("phone")
                ?: ""

            if (number.isNotBlank()) {
                viewModel.setDialedNumber(number)
                viewModel.selectTab(NavigationTab.KEYPAD)
            }
        }
    }

    private companion object {
        const val KEY_LOCKED = "app_locked"
    }
}

@Composable
fun AppLockScreen(errorMessage: String?, onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Pulse Dialer is Locked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Security verification required to access calls and contacts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onUnlock) {
                Text("Verify & Unlock")
            }
        }
    }
}
