package com.andsmarttv.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Detects OTG / USB external storage mounts and USB peripheral insertions.
 */
class OtgStorageReceiver(private val onStorageStateChanged: (isMounted: Boolean) -> Unit) :
    BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { ctx ->
            val isMounted = checkExternalStorageMounted(ctx)
            onStorageStateChanged.invoke(isMounted)
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addDataScheme("file")
        }
        val generalFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            context.registerReceiver(this, filter)
            context.registerReceiver(this, generalFilter)
        } catch (e: Exception) {
            // Register fallback
        }
        // Initial state check
        onStorageStateChanged.invoke(checkExternalStorageMounted(context))
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            // Ignored if not registered
        }
    }

    companion object {
        fun checkExternalStorageMounted(context: Context): Boolean {
            // Check secondary external storage volumes (USB Drives, SD Cards)
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            if (externalDirs.size > 1) {
                for (i in 1 until externalDirs.size) {
                    val dir = externalDirs[i]
                    if (dir != null && Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED) {
                        return true
                    }
                }
            }

            // Also check UsbManager for attached mass storage or USB devices
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            return (usbManager?.deviceList?.isNotEmpty() == true)
        }
    }
}
