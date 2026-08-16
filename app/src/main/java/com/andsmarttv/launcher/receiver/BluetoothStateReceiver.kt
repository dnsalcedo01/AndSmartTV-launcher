package com.andsmarttv.launcher.receiver

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class BluetoothStateReceiver(
    private val onBluetoothStateChanged: (Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            val isEnabled = (state == BluetoothAdapter.STATE_ON)
            onBluetoothStateChanged.invoke(isEnabled)
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(this, filter)
        // Emit current state
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val isEnabled = adapter?.isEnabled == true
        onBluetoothStateChanged.invoke(isEnabled)
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}
