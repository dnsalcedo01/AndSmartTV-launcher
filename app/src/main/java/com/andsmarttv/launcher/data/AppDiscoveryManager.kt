package com.andsmarttv.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import com.andsmarttv.launcher.data.model.AppInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Discovers and indexes both Android TV (Leanback) apps and sideloaded standard Android apps.
 */
class AppDiscoveryManager(
    private val context: Context,
    private val preferences: LauncherPreferences
) {
    private val packageManager: PackageManager = context.packageManager
    private val onlineBannerManager = OnlineBannerManager(context)

    /**
     * Loads all installed applications, deduplicating and applying user preferences (order, hidden status).
     */
    suspend fun loadInstalledApps(includeHidden: Boolean = false): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val appMap = LinkedHashMap<String, AppInfo>()

            // 1. Query Leanback TV Launchable Apps
            val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            }
            val leanbackApps: List<ResolveInfo> =
                packageManager.queryIntentActivities(leanbackIntent, PackageManager.MATCH_ALL)

            for (resolveInfo in leanbackApps) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName == context.packageName) continue // Skip self

                val actName = resolveInfo.activityInfo.name
                val key = "$pkgName/$actName"
                val label = resolveInfo.loadLabel(packageManager).toString()

                val banner = loadAppBanner(key, pkgName, resolveInfo)
                val icon = try {
                    resolveInfo.activityInfo.loadIcon(packageManager)
                        ?: resolveInfo.activityInfo.applicationInfo.loadIcon(packageManager)
                } catch (e: Exception) {
                    null
                }

                val app = AppInfo(
                    packageName = pkgName,
                    activityName = actName,
                    label = label,
                    isTvApp = true,
                    bannerDrawable = banner,
                    iconDrawable = icon
                )
                appMap[key] = app
            }

            // 2. Query Standard / Sideloaded Mobile Launchable Apps
            val mobileIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val mobileApps: List<ResolveInfo> =
                packageManager.queryIntentActivities(mobileIntent, PackageManager.MATCH_ALL)

            for (resolveInfo in mobileApps) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName == context.packageName) continue // Skip self

                val actName = resolveInfo.activityInfo.name
                val key = "$pkgName/$actName"

                // If already detected as TV app, don't overwrite
                if (appMap.containsKey(key)) continue

                val label = resolveInfo.loadLabel(packageManager).toString()
                val banner = loadAppBanner(key, pkgName, resolveInfo)
                val icon = try {
                    resolveInfo.activityInfo.loadIcon(packageManager)
                        ?: resolveInfo.activityInfo.applicationInfo.loadIcon(packageManager)
                } catch (e: Exception) {
                    null
                }

                val app = AppInfo(
                    packageName = pkgName,
                    activityName = actName,
                    label = label,
                    isTvApp = false,
                    bannerDrawable = banner,
                    iconDrawable = icon
                )
                appMap[key] = app
            }

            // 3. Apply Preferences (Hidden status, Favorites, Custom Order)
            val hiddenApps = preferences.getHiddenApps()
            val favorites = preferences.getFavorites()
            val savedOrder = preferences.getAppOrder()

            val allApps = appMap.values.map { app ->
                app.isHidden = hiddenApps.contains(app.uniqueKey)
                app.isFavorite = favorites.contains(app.uniqueKey)
                app
            }

            // Filter out hidden if not requested
            val filteredApps = if (includeHidden) allApps else allApps.filter { !it.isHidden }

            // 4. Sort based on custom saved order list
            val orderMap = savedOrder.withIndex().associate { it.value to it.index }
            filteredApps.sortedWith(Comparator { app1, app2 ->
                val pos1 = orderMap[app1.uniqueKey] ?: (Int.MAX_VALUE - 1000)
                val pos2 = orderMap[app2.uniqueKey] ?: (Int.MAX_VALUE - 1000)

                if (pos1 != pos2) {
                    pos1.compareTo(pos2)
                } else {
                    app1.label.compareTo(app2.label, ignoreCase = true)
                }
            })
        }

    private suspend fun loadAppBanner(uniqueKey: String, packageName: String, resolveInfo: ResolveInfo): Drawable? {
        // Priority 1: User-selected custom banner (stored in internal private files directory or URI)
        val customBannerPath = preferences.getCustomBannerPath(uniqueKey)
        if (customBannerPath != null) {
            try {
                val file = File(customBannerPath)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) return BitmapDrawable(context.resources, bmp)
                } else if (customBannerPath.startsWith("content://") || customBannerPath.startsWith("file://")) {
                    val uri = Uri.parse(customBannerPath)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) return BitmapDrawable(context.resources, bmp)
                    }
                } else {
                    val bmp = BitmapFactory.decodeFile(customBannerPath)
                    if (bmp != null) return BitmapDrawable(context.resources, bmp)
                }
            } catch (e: Exception) {
                // Fallback to default
            }
        }

        // Priority 2: Native Leanback 16:9 Banner
        val nativeBanner = try {
            resolveInfo.activityInfo.loadBanner(packageManager)
                ?: resolveInfo.activityInfo.applicationInfo.loadBanner(packageManager)
        } catch (e: Exception) {
            null
        }
        if (nativeBanner != null) return nativeBanner

        // Priority 3: Cached Community 16:9 Banner
        val cachedBanner = onlineBannerManager.getCachedBanner(packageName)
        if (cachedBanner != null) return cachedBanner

        return null
    }
}
