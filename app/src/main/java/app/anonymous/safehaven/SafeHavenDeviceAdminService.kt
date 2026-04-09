package app.anonymous.safehaven

import android.app.admin.DeviceAdminService
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import app.anonymous.safehaven.dpm.applyStrictRestrictions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class SafeHavenDeviceAdminService : DeviceAdminService() {

    private val packageReceiver = PackageChangeReceiver()
    private var isReceiverRegistered = false
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val policyObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            serviceScope.launch { enforceDaemonState() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
            priority = 999 
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        isReceiverRegistered = true

        runCatching {
            val cr = contentResolver
            arrayOf(
                Settings.Global.getUriFor("low_power"),
                Settings.Secure.getUriFor("accessibility_display_daltonizer_enabled"),
                Settings.Secure.getUriFor("accessibility_display_daltonizer"), 
                Settings.Global.getUriFor("window_animation_scale"),
                Settings.Global.getUriFor("transition_animation_scale"),
                Settings.Global.getUriFor("animator_duration_scale"),
                Settings.Global.getUriFor("private_dns_mode"),
                Settings.Global.getUriFor("private_dns_specifier"),
                Settings.System.getUriFor("system_locales"),
                Settings.Secure.getUriFor("night_display_auto_mode"),
                Settings.Secure.getUriFor("night_display_color_temperature"),
                Settings.Secure.getUriFor("night_display_custom_start_time"),
                Settings.Secure.getUriFor("night_display_custom_end_time"),
                Settings.Secure.getUriFor("night_display_activated"),
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
                Settings.Global.getUriFor(Settings.Global.ADB_ENABLED)
            ).forEach { uri -> cr.registerContentObserver(uri, false, policyObserver) }
            
            Log.d("AdminService", "Service bound and monitoring system state.")
        }.onFailure { Log.e("AdminService", "Failed to bind ContentObservers", it) }
    }

    private fun enforceDaemonState() {
        val cr = contentResolver
        
        // --- 1. BATTERY SAVER ENFORCEMENT ---
        runCatching {
            if (Settings.Global.getInt(cr, "low_power", 0) == 0) {
                val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                val isUnplugged = plugged == 0 || plugged == -1 

                if (isUnplugged) {
                    Settings.Global.putInt(cr, "low_power", 1)
                    Settings.Global.putInt(cr, "low_power_sticky", 1)
                    Settings.Global.putInt(cr, "low_power_sticky_auto_disable_enabled", 0)
                    Settings.Global.putInt(cr, "automatic_power_save_mode", 0)
                    Settings.Global.putInt(cr, "low_power_trigger_level", 100)
                }
            }
        }.onFailure { Log.e("AdminService", "Battery Saver write failed", it) }

        // --- 2. GRAYSCALE ENFORCEMENT PATCH ---
        runCatching {
            val isEnabled = Settings.Secure.getInt(cr, "accessibility_display_daltonizer_enabled", 0) == 1
            val mode = Settings.Secure.getInt(cr, "accessibility_display_daltonizer", -1)
            
            if (!isEnabled || mode != 0) {
                Settings.Secure.putInt(cr, "accessibility_display_daltonizer", 0)
                Settings.Secure.putInt(cr, "accessibility_display_daltonizer_enabled", 1)
                Log.d("AdminService", "Color bypass squashed. Forced Grayscale.")
            }
        }.onFailure { Log.e("AdminService", "Grayscale write failed", it) }

        // --- 3. ZERO ANIMATION ENFORCEMENT ---
        runCatching {
            if (Settings.Global.getFloat(cr, "window_animation_scale", 1.0f) != 0.0f) {
                Settings.Global.putFloat(cr, "window_animation_scale", 0.0f)
                Settings.Global.putFloat(cr, "transition_animation_scale", 0.0f)
                Settings.Global.putFloat(cr, "animator_duration_scale", 0.0f)
            }
        }.onFailure { Log.e("AdminService", "Animation write failed", it) }

        // --- 4. MULLVAD DNS ENFORCEMENT ---
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Settings.Global.getString(cr, "private_dns_mode") != "hostname") {
                Settings.Global.putString(cr, "private_dns_mode", "hostname")
                Settings.Global.putString(cr, "private_dns_specifier", "all.dns.mullvad.net")
                Privilege.DPM.setGlobalSetting(Privilege.DAR, "private_dns_mode", "hostname")
                Privilege.DPM.setGlobalSetting(Privilege.DAR, "private_dns_specifier", "all.dns.mullvad.net")
            }
        }.onFailure { Log.e("AdminService", "DNS write failed", it) }
        
        // --- 5. NIGHT LIGHT ENFORCEMENT ---
        runCatching {
            if (Settings.Secure.getInt(cr, "night_display_auto_mode", 0) != 1 || 
                Settings.Secure.getInt(cr, "night_display_custom_start_time", 0) != 79200000 ||
                Settings.Secure.getInt(cr, "night_display_custom_end_time", 0) != 21600000 ||
                Settings.Secure.getInt(cr, "night_display_color_temperature", 0) != 2500) {
                
                Settings.Secure.putInt(cr, "night_display_auto_mode", 1)
                Settings.Secure.putInt(cr, "night_display_custom_start_time", 79200000) 
                Settings.Secure.putInt(cr, "night_display_custom_end_time", 21600000)   
                Settings.Secure.putInt(cr, "night_display_color_temperature", 2500)
            }
            
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val isNightTime = hour >= 22 || hour < 6
            if (isNightTime && Settings.Secure.getInt(cr, "night_display_activated", 0) == 0) {
                Settings.Secure.putInt(cr, "night_display_activated", 1)
            }
        }.onFailure { Log.e("AdminService", "Night light write failed", it) }

        // --- 6. DEVELOPER OPTIONS GHOST TRAP ---
        if (SP.hasRebootedSinceSetup) {
            runCatching {
                if (Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0) {
                    Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                    Log.d("AdminService", "Developer Options bypass squashed.")
                }
                if (Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) != 0) {
                    Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0)
                    Log.d("AdminService", "ADB bypass squashed.")
                }
            }.onFailure { Log.e("AdminService", "Failed to squash Dev Options", it) }
        }
        
        // --- 7. DYNAMIC RESTRICTION SYNC ---
        // This will automatically lock Locale to German (if conditions are met)
        // AND enforce DISALLOW_DEBUGGING_FEATURES instantly after the ghost trap above ensures they are explicitly 0.
        applyStrictRestrictions(this)
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(packageReceiver) }
            isReceiverRegistered = false
        }
        runCatching { contentResolver.unregisterContentObserver(policyObserver) }
        super.onDestroy()
    }
}