package com.example.data.repository

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.model.SimInfo

class TelecomRepository(private val context: Context) {

    private companion object {
        const val TAG = "TelecomRepository"
    }

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun isCallPhonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isReadPhoneStateGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isDefaultDialer(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
                } else {
                    telecomManager?.defaultDialerPackage == context.packageName
                }
            } else {
                telecomManager?.defaultDialerPackage == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    fun createRequestDefaultDialerIntent(): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getAvailableSims(): List<SimInfo> {
        val list = mutableListOf<SimInfo>()
        if (!isReadPhoneStateGranted()) return list

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            subscriptionManager?.activeSubscriptionInfoList?.forEach { subInfo ->
                list.add(
                    SimInfo(
                        subscriptionId = subInfo.subscriptionId,
                        slotIndex = subInfo.simSlotIndex,
                        displayName = subInfo.displayName?.toString() ?: "SIM ${subInfo.simSlotIndex + 1}",
                        carrierName = subInfo.carrierName?.toString() ?: ""
                    )
                )
            }
        } catch (_: SecurityException) {
            // Permission missing
        } catch (_: Exception) {
            // Safe fallback
        }

        return list
    }

    fun isEmergencyNumber(number: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager?.isEmergencyNumber(number) ?: false
            } else {
                number == "911" || number == "112" || number == "999" || number == "000"
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Places a phone call. Returns false if the call could not be started.
     *
     * As the default dialer, every call (emergency numbers included) goes through
     * [TelecomManager.placeCall]: Telecom lets the default dialer place emergency calls, and an
     * ACTION_DIAL hand-off would only come back to this app's own keypad.
     *
     * When this app is not the default dialer, normal numbers use ACTION_CALL and emergency
     * numbers are handed to the system dialer with ACTION_DIAL (ACTION_CALL refuses them).
     */
    @android.annotation.SuppressLint("MissingPermission") // SecurityException is caught below
    fun placeCall(number: String, simSubscriptionId: Int? = null): Boolean {
        if (number.isBlank()) return false
        val uri = Uri.fromParts("tel", number, null)
        val emergency = isEmergencyNumber(number)

        if (isDefaultDialer() && telecomManager != null) {
            return try {
                val extras = Bundle()
                // Let Telecom pick the right SIM for emergency calls.
                if (!emergency && simSubscriptionId != null && simSubscriptionId >= 0) {
                    findPhoneAccountHandleForSubscription(simSubscriptionId)?.let { handle ->
                        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                    }
                }
                telecomManager.placeCall(uri, extras)
                true
            } catch (e: SecurityException) {
                android.util.Log.e(TAG, "placeCall rejected (missing CALL_PHONE?)", e)
                false
            } catch (e: Exception) {
                android.util.Log.e(TAG, "placeCall failed", e)
                false
            }
        }

        // Not the default dialer: another app owns ACTION_DIAL, so handing off is safe.
        if (emergency || !isCallPhonePermissionGranted()) {
            return startSafely(Intent(Intent.ACTION_DIAL, uri))
        }
        return startSafely(Intent(Intent.ACTION_CALL, uri)) || startSafely(Intent(Intent.ACTION_DIAL, uri))
    }

    private fun startSafely(intent: Intent): Boolean = try {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Could not start ${intent.action}", e)
        false
    }

    @android.annotation.SuppressLint("MissingPermission") // guarded by isReadPhoneStateGranted() below
    private fun findPhoneAccountHandleForSubscription(subscriptionId: Int): PhoneAccountHandle? {
        // callCapablePhoneAccounts requires READ_PHONE_STATE; bail out instead of relying on the catch.
        if (!isReadPhoneStateGranted()) return null
        try {
            val accounts = telecomManager?.callCapablePhoneAccounts ?: return null
            for (handle in accounts) {
                if (handle.id.contains(subscriptionId.toString())) {
                    return handle
                }
            }
        } catch (_: Exception) {
            // Safe fallback
        }
        return null
    }
}
