package app.anonymous.safehaven

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Global Thread-Safe Memory State for Device Owner Permissions.
 */
object Privilege {
    lateinit var DPM: DevicePolicyManager
        private set

    lateinit var DAR: ComponentName
        private set

    data class Status(
        val device: Boolean = false,
        val profile: Boolean = false,
        val work: Boolean = false,
        val org: Boolean = false,
        val affiliated: Boolean = false
    ) {
        val activated: Boolean get() = device || profile
        val primary: Boolean get() = Binder.getCallingUid() / 100000 == 0
    }

    val status = MutableStateFlow(Status())
    val lockdownActive = MutableStateFlow(false)

    fun initialize(context: Context) {
        if (!::DPM.isInitialized) {
            DPM = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            DAR = ComponentName(context, Receiver::class.java)
        }
        updateStatus()
    }

    fun updateStatus() {
        val profile = DPM.isProfileOwnerApp(DAR.packageName)
        val work = profile && DPM.isManagedProfile(DAR)

        status.value = Status(
            device = DPM.isDeviceOwnerApp(DAR.packageName),
            profile = profile,
            work = work,
            org = work && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && DPM.isOrganizationOwnedDeviceWithManagedProfile,
            affiliated = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && DPM.isAffiliatedUser
        )
    }
}