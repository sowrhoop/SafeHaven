package app.anonymous.safehaven

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Whitelist helper.
 * Default entries are provided via BuildConfig.APP_WHITELIST at build time.
 * Designed for efficient lookups and runtime caching.
 */
object AppWhitelist {

    // Default whitelist values are supplied at build time via BuildConfig.
    private val essentialApps: Set<String> = BuildConfig.APP_WHITELIST
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    @Volatile
    private var cachedWhitelist: Set<String>? = null
    private val mutex = Mutex()
    
    // O(1) Synchronous Lookup
    fun isWhitelistedSync(packageName: String): Boolean {
        cachedWhitelist?.let { return it.contains(packageName) }
        val activeSet = SP.appWhitelist.orEmpty().split(",").filter { it.isNotEmpty() }.toSet() + essentialApps
        cachedWhitelist = activeSet
        return activeSet.contains(packageName)
    }

    suspend fun getWhitelist(): Set<String> = cachedWhitelist ?: withContext(Dispatchers.IO) {
        mutex.withLock {
            cachedWhitelist ?: (SP.appWhitelist.orEmpty().split(",").filter { it.isNotEmpty() }.toSet() + essentialApps).also { 
                cachedWhitelist = it 
            }
        }
    }
    
    suspend fun setWhitelist(packages: Set<String>) = withContext(Dispatchers.IO) {
        val cleanSet = packages.filter { it.isNotEmpty() }.toSet()
        mutex.withLock {
            cachedWhitelist = cleanSet + essentialApps
            SP.appWhitelist = cleanSet.joinToString(",")
        }
    }
    
    suspend fun addToWhitelist(packageName: String) = setWhitelist(getWhitelist() + packageName)
    
    suspend fun removeFromWhitelist(packageName: String) = setWhitelist(getWhitelist() - packageName)
    
    suspend fun getInstalledUserApps(context: Context): List<ApplicationInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES
        
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getInstalledApplications(flags)
        }
        
        apps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
    }
    
    suspend fun enforceWhitelist(context: Context) = withContext(Dispatchers.IO) {
        if (!Privilege.status.value.device) return@withContext
        
        val whitelist = getWhitelist()
        val (toUnsuspend, toSuspend) = getInstalledUserApps(context)
            .map { it.packageName }
            .partition { it in whitelist }

        runCatching {
            if (toUnsuspend.isNotEmpty()) Privilege.DPM.setPackagesSuspended(Privilege.DAR, toUnsuspend.toTypedArray(), false)
            if (toSuspend.isNotEmpty()) Privilege.DPM.setPackagesSuspended(Privilege.DAR, toSuspend.toTypedArray(), true)
        }
    }
    
    fun initializeDefaultWhitelist() {
        if (SP.appWhitelist == null) SP.appWhitelist = ""
    }
}