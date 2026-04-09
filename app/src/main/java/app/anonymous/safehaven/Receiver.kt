package app.anonymous.safehaven

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.anonymous.safehaven.dpm.handlePrivilegeChange
import app.anonymous.safehaven.dpm.retrieveSecurityLogs

class Receiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Privilege.updateStatus()
        Log.d("AdminDaemon", "Kernel privileges locked in.")
        handlePrivilegeChange(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Privilege.updateStatus()
        Log.d("AdminDaemon", "Kernel privileges revoked.")
    }

    override fun onSecurityLogsAvailable(context: Context, intent: Intent) {
        super.onSecurityLogsAvailable(context, intent)
        retrieveSecurityLogs()
    }
}