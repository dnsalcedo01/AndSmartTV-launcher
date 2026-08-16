package com.andsmarttv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream

/**
 * Handles persistent preferences for custom app ordering, hidden apps, and launcher layout settings.
 */
class LauncherPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "andsmarttv_launcher_prefs"
        private const val KEY_APP_ORDER = "key_app_order"
        private const val KEY_HIDDEN_APPS = "key_hidden_apps"
        private const val KEY_FAVORITES = "key_favorites"
        private const val KEY_GRID_COLUMNS = "key_grid_columns"
        private const val KEY_SHOW_STATUS_BAR = "key_show_status_bar"

        const val DEFAULT_GRID_COLUMNS = 4
        const val MAX_FAVORITES = 4
        private const val KEY_SELECTED_THEME = "key_selected_theme"
        private const val KEY_IS_DARK_MODE = "key_is_dark_mode"

        const val THEME_MIDNIGHT = 0
        const val THEME_SUNSET = 1
        const val THEME_EMERALD = 2
        const val THEME_PURPLE = 3
        const val THEME_MONET_BLUE = 4

        private const val KEY_AUTO_FETCH_BANNERS = "key_auto_fetch_banners"
    }

    fun isAutoFetchBannersEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_FETCH_BANNERS, true)
    }

    fun setAutoFetchBannersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FETCH_BANNERS, enabled).apply()
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean(KEY_IS_DARK_MODE, true)
    }

    fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_IS_DARK_MODE, isDark).apply()
    }

    fun getThemeIndex(): Int {
        return prefs.getInt(KEY_SELECTED_THEME, THEME_MIDNIGHT)
    }

    fun setThemeIndex(themeIndex: Int) {
        prefs.edit().putInt(KEY_SELECTED_THEME, themeIndex).apply()
    }

    /**
     * Get saved order list of app uniqueKeys ("packageName/activityName").
     */
    private val KEY_APP_ORDER = "key_app_order"
    private val KEY_FAVORITES_ORDER = "key_favorites_order"

    /**
     * Get app order list.
     */
    fun getAppOrder(): List<String> {
        val json = prefs.getString(KEY_APP_ORDER, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save app order list.
     */
    fun saveAppOrder(orderList: List<String>) {
        val json = gson.toJson(orderList)
        prefs.edit().putString(KEY_APP_ORDER, json).apply()
    }

    /**
     * Get favorites order list.
     */
    fun getFavoritesOrder(): List<String> {
        val json = prefs.getString(KEY_FAVORITES_ORDER, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save favorites order list.
     */
    fun saveFavoritesOrder(orderList: List<String>) {
        val json = gson.toJson(orderList)
        prefs.edit().putString(KEY_FAVORITES_ORDER, json).apply()
    }

    /**
     * Get hidden apps set.
     */
    fun getHiddenApps(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()
    }

    /**
     * Toggle or set app hidden state.
     */
    fun setAppHidden(uniqueKey: String, hidden: Boolean) {
        val hiddenSet = getHiddenApps().toMutableSet()
        if (hidden) {
            hiddenSet.add(uniqueKey)
        } else {
            hiddenSet.remove(uniqueKey)
        }
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, hiddenSet).apply()
    }

    fun unhideAll() {
        prefs.edit().remove(KEY_HIDDEN_APPS).apply()
    }

    /**
     * Get favorite apps set.
     */
    fun getFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    /**
     * Toggle or set favorite app state. Returns true if successfully set/updated, false if blocked by MAX_FAVORITES.
     */
    fun setFavorite(uniqueKey: String, isFavorite: Boolean): Boolean {
        val favSet = getFavorites().toMutableSet()
        if (isFavorite) {
            if (!favSet.contains(uniqueKey) && favSet.size >= MAX_FAVORITES) {
                return false
            }
            favSet.add(uniqueKey)
        } else {
            favSet.remove(uniqueKey)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favSet).apply()
        return true
    }

    fun isFavorite(uniqueKey: String): Boolean {
        return getFavorites().contains(uniqueKey)
    }

    fun toggleFavorite(uniqueKey: String): Boolean {
        return if (isFavorite(uniqueKey)) {
            setFavorite(uniqueKey, false)
            false
        } else {
            setFavorite(uniqueKey, true)
        }
    }

    /**
     * Custom user-selected 16:9 banner image URI or file path.
     */
    fun getCustomBannerPath(uniqueKey: String): String? {
        return prefs.getString("custom_banner_$uniqueKey", null)
    }

    fun setCustomBannerPath(uniqueKey: String, path: String?) {
        if (path != null) {
            prefs.edit().putString("custom_banner_$uniqueKey", path).apply()
        } else {
            prefs.edit().remove("custom_banner_$uniqueKey").apply()
        }
    }

    /**
     * Saves a user-selected image from an external Uri directly into the app's internal private files storage.
     * This guarantees 100% reboot survival without relying on transient content:// URI permissions.
     */
    fun saveCustomBanner(uniqueKey: String, sourceUri: Uri): String? {
        return try {
            val bannersDir = File(context.filesDir, "custom_banners").apply { mkdirs() }
            val safeFileName = uniqueKey.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".png"
            val destFile = File(bannersDir, safeFileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val originalBmp = BitmapFactory.decodeStream(input)
                if (originalBmp != null) {
                    FileOutputStream(destFile).use { out ->
                        originalBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    setCustomBannerPath(uniqueKey, destFile.absolutePath)
                    destFile.absolutePath
                } else null
            }
        } catch (e: Exception) {
            Log.e("LauncherPreferences", "Failed to save custom banner for $uniqueKey", e)
            null
        }
    }

    /**
     * Deletes any locally saved custom banner file for an app and clears the preference.
     */
    fun deleteCustomBanner(uniqueKey: String) {
        try {
            val existingPath = getCustomBannerPath(uniqueKey)
            if (existingPath != null) {
                val file = File(existingPath)
                if (file.exists()) file.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
        setCustomBannerPath(uniqueKey, null)
    }

    /**
     * Number of grid columns (default 4).
     */
    fun getGridColumns(): Int {
        return DEFAULT_GRID_COLUMNS
    }

    fun setGridColumns(columns: Int) {
        prefs.edit().putInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS).apply()
    }

    fun isStatusBarEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHOW_STATUS_BAR, true)
    }

    fun setStatusBarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_STATUS_BAR, enabled).apply()
    }
}
