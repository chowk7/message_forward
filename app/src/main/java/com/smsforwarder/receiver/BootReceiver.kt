package com.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // SMS receiver is registered in manifest, so it will work after boot.
            // This receiver ensures the app is aware of boot events if needed.
        }
    }
}
