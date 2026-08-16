package com.andsmarttv.launcher.data.model

import android.graphics.drawable.Drawable

/**
 * Represents an installed Android application.
 */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    var label: String,
    val isTvApp: Boolean,
    var bannerDrawable: Drawable? = null,
    var iconDrawable: Drawable? = null,
    var isFavorite: Boolean = false,
    var isHidden: Boolean = false,
    var customOrder: Int = 0
) {
    val uniqueKey: String
        get() = "$packageName/$activityName"
}
