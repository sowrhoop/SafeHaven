package app.anonymous.safehaven

import android.app.Activity
import android.os.Bundle

/**
 * Pure Ghost Activity. Zero UI execution.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish() 
    }
}