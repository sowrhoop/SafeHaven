package app.anonymous.safehaven.dpm

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import app.anonymous.safehaven.AppWhitelist
import app.anonymous.safehaven.BuildConfig
import app.anonymous.safehaven.Privilege
import app.anonymous.safehaven.Privilege.DAR
import app.anonymous.safehaven.Privilege.DPM
import app.anonymous.safehaven.SP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// Dynamically loaded from CI/CD Secrets
private val BATTERY_SAVER_FLAGS = BuildConfig.BATTERY_SAVER_FLAGS

fun retrieveSecurityLogs() {
    CoroutineScope(Dispatchers.IO).launch { runCatching { DPM.retrieveSecurityLogs(DAR) } }
}

fun setDefaultAffiliationID() {
    if (SP.isDefaultAffiliationIdSet) return
    runCatching {
        DPM.setAffiliationIds(DAR, setOf("SafeHaven_default_affiliation_id"))
        SP.isDefaultAffiliationIdSet = true
    }
}

fun handlePrivilegeChange(context: Context) {
    val activated = Privilege.status.value.activated
    SP.shortcuts = activated
    if (!activated) {
        SP.isDefaultAffiliationIdSet = false
        return
    }
    setDefaultAffiliationID()
    if (Privilege.status.value.device) CoroutineScope(Dispatchers.IO).launch { enforceAllPolicies(context) }
}

suspend fun enforceAllPolicies(context: Context) = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        applyChromiumPolicies()
        enforcePrivateDns(context)
    }
    
    runCatching { DPM.setUninstallBlocked(DAR, context.packageName, true) }

    enforceBatterySaverFlags(context)
    enforceNightLight(context)
    enforceGrayscale(context)
    enforceAnimations(context)
    squashDeveloperOptions(context)
    applyStrictRestrictions(context) 
    enforceStrictWhitelist(context)
}

private fun squashDeveloperOptions(context: Context) = runCatching {
    if (!SP.hasRebootedSinceSetup) return@runCatching
    val cr = context.contentResolver
    
    if (Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0) {
        Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
    }
    if (Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) != 0) {
        Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun enforcePrivateDns(context: Context) = runCatching {
    val cr = context.contentResolver
    Settings.Global.putString(cr, "private_dns_mode", "hostname")
    // Dynamically loaded from CI/CD Secrets
    Settings.Global.putString(cr, "private_dns_specifier", BuildConfig.PRIVATE_DNS)
    DPM.setGlobalSetting(DAR, "private_dns_mode", "hostname")
    DPM.setGlobalSetting(DAR, "private_dns_specifier", BuildConfig.PRIVATE_DNS)
}

private fun enforceNightLight(context: Context) = runCatching {
    val cr = context.contentResolver
    Settings.Secure.putInt(cr, "night_display_auto_mode", 1) 
    Settings.Secure.putInt(cr, "night_display_custom_start_time", 79200000) 
    Settings.Secure.putInt(cr, "night_display_custom_end_time", 21600000) 
    Settings.Secure.putInt(cr, "night_display_color_temperature", 2500)

    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val isNightTime = hour >= 22 || hour < 6
    if (isNightTime) {
        Settings.Secure.putInt(cr, "night_display_activated", 1)
    }
}

private fun enforceGrayscale(context: Context) = runCatching {
    val cr = context.contentResolver
    val isEnabled = Settings.Secure.getInt(cr, "accessibility_display_daltonizer_enabled", 0) == 1
    val mode = Settings.Secure.getInt(cr, "accessibility_display_daltonizer", -1)
    
    if (!isEnabled || mode != 0) {
        Settings.Secure.putInt(cr, "accessibility_display_daltonizer", 0)
        Settings.Secure.putInt(cr, "accessibility_display_daltonizer_enabled", 1)
    }
}

private fun enforceAnimations(context: Context) = runCatching {
    val cr = context.contentResolver
    Settings.Global.putFloat(cr, "window_animation_scale", 0.0f)
    Settings.Global.putFloat(cr, "transition_animation_scale", 0.0f)
    Settings.Global.putFloat(cr, "animator_duration_scale", 0.0f)
}

private fun enforceBatterySaverFlags(context: Context) = runCatching {
    val cr = context.contentResolver
    Settings.Global.putString(cr, "battery_saver_constants", BATTERY_SAVER_FLAGS)
    Settings.Global.putString(cr, "battery_saver_device_specific_constants", BATTERY_SAVER_FLAGS)
    Settings.Global.putInt(cr, "automatic_power_save_mode", 0)
    Settings.Global.putInt(cr, "low_power_trigger_level", 100)
    Settings.Global.putInt(cr, "low_power", 1)
    Settings.Global.putInt(cr, "low_power_sticky", 1)
    Settings.Global.putInt(cr, "low_power_sticky_auto_disable_enabled", 0)
}

private fun applyChromiumPolicies() {
    val bundle = Bundle().apply {
        // --- 1. CORE NETWORK & DNS ---
        putString("DnsOverHttpsMode", "secure")
        putString("DnsOverHttpsTemplates", "https://${BuildConfig.PRIVATE_DNS}/dns-query")
        putString("WebRtcIPHandling", "disable_non_proxied_udp")
        putBoolean("SitePerProcess", true) // Strict Site Isolation
        putInt("NetworkPredictionOptions", 2) // 2 = Disable prefetching (Saves data, stops background tracking)
        
        // --- 2. DOPAMINE DETOX & ANTI-RABBIT HOLE ---
        putInt("IncognitoModeAvailability", 1) // 1 = Disabled
        putBoolean("SearchSuggestEnabled", false) // Kill predictive search suggestions
        putBoolean("PromotionalTabsEnabled", false)
        
        // --- 3. ANTI-CONVENIENCE (Force Intentionality) ---
        putInt("BrowserSignin", 0) // 0 = Disable browser sign-in completely
        putBoolean("SyncDisabled", true)
        putBoolean("PasswordManagerEnabled", false)
        putBoolean("AutofillAddressEnabled", false)
        putBoolean("AutofillCreditCardEnabled", false)
        
        // --- 4. ABSOLUTE PRIVACY ---
        putBoolean("DisableScreenshots", true) // Prevents other apps from scraping the browser screen
        putBoolean("MetricsReportingEnabled", false)
        putBoolean("UrlKeyedAnonymizedDataCollectionEnabled", false)
    }
    
    arrayOf("com.android.chrome", "com.brave.browser").forEach { pkg ->
        runCatching { DPM.setApplicationRestrictions(DAR, pkg, bundle) }
    }
}

fun applyStrictRestrictions(context: Context) {
    val desiredRestrictions = buildSet {
        add(UserManager.DISALLOW_ADD_USER)
        add(UserManager.DISALLOW_REMOVE_USER)
        add(UserManager.DISALLOW_SAFE_BOOT) 
        add(UserManager.DISALLOW_FACTORY_RESET)
        add(UserManager.DISALLOW_CONFIG_DATE_TIME)
        add(UserManager.DISALLOW_ADD_MANAGED_PROFILE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(UserManager.DISALLOW_USER_SWITCH)
            if (Locale.getDefault().language == "de") {
                add(UserManager.DISALLOW_CONFIG_LOCALE)
                Log.d("Lockdown", "German verified. Locale locked.")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        }
        if (SP.hasRebootedSinceSetup) {
            val cr = context.contentResolver
            val devOptionsOff = Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 0
            val adbOff = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 0
            
            if (devOptionsOff && adbOff) {
                add(UserManager.DISALLOW_DEBUGGING_FEATURES)
                Log.d("Lockdown", "Dev Options and ADB verified off. DEBUGGING_FEATURES locked.")
            }
        }
    }

    runCatching {
        val activeRestrictions = DPM.getUserRestrictions(DAR)
        activeRestrictions.keySet().forEach { currentRestriction ->
            if (currentRestriction !in desiredRestrictions) {
                DPM.clearUserRestriction(DAR, currentRestriction)
            }
        }
        desiredRestrictions.forEach { DPM.addUserRestriction(DAR, it) }
    }.onFailure { Log.e("Lockdown", "Failed to sync restrictions", it) }
}

private fun hideLauncherIcon(context: Context) = runCatching {
    val componentName = ComponentName(context, "app.anonymous.safehaven.MainActivity")
    val pm = context.packageManager
    if (pm.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
        pm.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}

suspend fun enforceStrictWhitelist(context: Context) = withContext(Dispatchers.IO) {
    if (!Privilege.status.value.device) return@withContext
    Privilege.lockdownActive.value = true

    try {
        hideLauncherIcon(context)
        val whitelist = AppWhitelist.getWhitelist()
        val installedApps = AppWhitelist.getInstalledUserApps(context)
        val (toUnsuspend, toSuspend) = installedApps.map { it.packageName }.partition { it in whitelist }

        if (toUnsuspend.isNotEmpty()) runCatching { DPM.setPackagesSuspended(DAR, toUnsuspend.toTypedArray(), false) }
        if (toSuspend.isNotEmpty()) runCatching { DPM.setPackagesSuspended(DAR, toSuspend.toTypedArray(), true) }
    } finally {
        Privilege.lockdownActive.value = false
    }
}