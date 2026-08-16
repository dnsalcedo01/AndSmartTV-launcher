package com.andsmarttv.launcher.ui.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.core.widget.TextViewCompat
import com.andsmarttv.launcher.R
import com.andsmarttv.launcher.data.AppDiscoveryManager
import com.andsmarttv.launcher.data.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsOverlayDialog(
    context: Context,
    private val preferences: LauncherPreferences,
    private val discoveryManager: AppDiscoveryManager,
    private val onSettingsChanged: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_settings)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val dialogContainer: View = findViewById(R.id.dialogContainer)
        val tvSettingsTitle: TextView = findViewById(R.id.tvSettingsTitle)
        val tvHeaderDisplayMode: TextView = findViewById(R.id.tvHeaderDisplayMode)
        val tvHeaderAmbientTheme: TextView = findViewById(R.id.tvHeaderAmbientTheme)

        val div1: View = findViewById(R.id.div1)
        val div2: View = findViewById(R.id.div2)

        // Mode Toggles
        val btnModeDark: Button = findViewById(R.id.btnModeDark)
        val btnModeLight: Button = findViewById(R.id.btnModeLight)

        // Themes
        val btnThemeMidnight: Button = findViewById(R.id.btnThemeMidnight)
        val btnThemeSunset: Button = findViewById(R.id.btnThemeSunset)
        val btnThemeEmerald: Button = findViewById(R.id.btnThemeEmerald)
        val btnThemePurple: Button = findViewById(R.id.btnThemePurple)

        // Hidden & System & About
        val btnManageHidden: Button = findViewById(R.id.btnManageHidden)
        val btnAboutLauncher: Button = findViewById(R.id.btnAboutLauncher)
        val btnSystemSettings: Button = findViewById(R.id.btnSystemSettings)

        val allButtons = listOf(
            btnModeDark, btnModeLight,
            btnThemeMidnight, btnThemeSunset, btnThemeEmerald, btnThemePurple,
            btnManageHidden, btnAboutLauncher, btnSystemSettings
        )

        fun applyAdaptiveTheming() {
            val isDark = preferences.isDarkMode()
            val themeIndex = preferences.getThemeIndex()
            val bgRes = if (isDark) {
                when (themeIndex) {
                    LauncherPreferences.THEME_SUNSET -> R.drawable.bg_dialog_glass_sunset
                    LauncherPreferences.THEME_EMERALD -> R.drawable.bg_dialog_glass_emerald
                    LauncherPreferences.THEME_PURPLE -> R.drawable.bg_dialog_glass_purple
                    else -> R.drawable.bg_dialog_glass_dark
                }
            } else {
                when (themeIndex) {
                    LauncherPreferences.THEME_SUNSET -> R.drawable.bg_dialog_glass_light_sunset
                    LauncherPreferences.THEME_EMERALD -> R.drawable.bg_dialog_glass_light_emerald
                    LauncherPreferences.THEME_PURPLE -> R.drawable.bg_dialog_glass_light_purple
                    else -> R.drawable.bg_dialog_glass_light
                }
            }
            dialogContainer.setBackgroundResource(bgRes)

            val primaryTextColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#0F172A")
            val secondaryTextColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
            val dividerColor = if (isDark) Color.parseColor("#20FFFFFF") else Color.parseColor("#200F172A")
            val btnNormalSelector = if (isDark) R.drawable.btn_dialog_solid_selector else R.drawable.btn_dialog_light_selector
            val btnActiveSelector = if (isDark) R.drawable.btn_dialog_active_selector else R.drawable.btn_dialog_light_active_selector

            tvSettingsTitle.setTextColor(primaryTextColor)
            tvHeaderDisplayMode.setTextColor(secondaryTextColor)
            tvHeaderAmbientTheme.setTextColor(secondaryTextColor)

            div1.setBackgroundColor(dividerColor)
            div2.setBackgroundColor(dividerColor)

            val states = arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            )
            val textColors = if (isDark) {
                intArrayOf(
                    Color.parseColor("#0F172A"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#FFFFFF")
                )
            } else {
                intArrayOf(
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#0F172A")
                )
            }
            val iconColors = if (isDark) {
                intArrayOf(
                    Color.parseColor("#0F172A"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#FFFFFF")
                )
            } else {
                intArrayOf(
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#334155")
                )
            }
            val textColorList = ColorStateList(states, textColors)
            val iconColorList = ColorStateList(states, iconColors)

            allButtons.forEach { btn ->
                btn.stateListAnimator = null
                btn.elevation = 0f
                btn.setBackgroundResource(btnNormalSelector)
                btn.setTextColor(textColorList)
                TextViewCompat.setCompoundDrawableTintList(btn, iconColorList)
            }

            // Highlight Active Display Mode
            if (isDark) {
                btnModeDark.setBackgroundResource(btnActiveSelector)
            } else {
                btnModeLight.setBackgroundResource(btnActiveSelector)
            }

            // Highlight Active Theme
            when (themeIndex) {
                LauncherPreferences.THEME_SUNSET -> btnThemeSunset.setBackgroundResource(btnActiveSelector)
                LauncherPreferences.THEME_EMERALD -> btnThemeEmerald.setBackgroundResource(btnActiveSelector)
                LauncherPreferences.THEME_PURPLE -> btnThemePurple.setBackgroundResource(btnActiveSelector)
                else -> btnThemeMidnight.setBackgroundResource(btnActiveSelector)
            }

            if (isDark) {
                btnThemeMidnight.text = "Midnight"
                btnThemeSunset.text = "Sunset"
                btnThemeEmerald.text = "Emerald"
                btnThemePurple.text = "Purple"
            } else {
                btnThemeMidnight.text = "Slate Frost"
                btnThemeSunset.text = "Sunrise"
                btnThemeEmerald.text = "Mint Sage"
                btnThemePurple.text = "Lavender"
            }
        }
        applyAdaptiveTheming()

        fun updateHiddenCount() {
            val hiddenApps = preferences.getHiddenApps()
            btnManageHidden.text = "View & Restore Hidden Apps (${hiddenApps.size})"
        }
        updateHiddenCount()

        // Mode switch (Live preview without dismissing dialog)
        btnModeDark.setOnClickListener {
            preferences.setDarkMode(true)
            applyAdaptiveTheming()
            onSettingsChanged.invoke()
        }
        btnModeLight.setOnClickListener {
            preferences.setDarkMode(false)
            applyAdaptiveTheming()
            onSettingsChanged.invoke()
        }

        // Theme clicks (Live preview without dismissing dialog)
        btnThemeMidnight.setOnClickListener {
            preferences.setThemeIndex(LauncherPreferences.THEME_MIDNIGHT)
            applyAdaptiveTheming()
            onSettingsChanged.invoke()
        }
        btnThemeSunset.setOnClickListener {
            preferences.setThemeIndex(LauncherPreferences.THEME_SUNSET)
            applyAdaptiveTheming()
            onSettingsChanged.invoke()
        }
        btnThemeEmerald.setOnClickListener {
            preferences.setThemeIndex(LauncherPreferences.THEME_EMERALD)
            applyAdaptiveTheming()
            onSettingsChanged.invoke()
        }
        btnThemePurple.setOnClickListener {
            preferences.setThemeIndex(LauncherPreferences.THEME_PURPLE)
            applyAdaptiveTheming()
            onSettingsChanged.invoke()
        }

        btnManageHidden.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val allApps = discoveryManager.loadInstalledApps(includeHidden = true)
                val hiddenApps = allApps.filter { it.isHidden }
                hide()
                val hiddenDialog = HiddenAppsDialog(
                    context = context,
                    allHiddenApps = hiddenApps,
                    preferences = preferences,
                    onAppsChanged = {
                        updateHiddenCount()
                        onSettingsChanged.invoke()
                    }
                )
                hiddenDialog.setOnDismissListener {
                    show()
                    updateHiddenCount()
                }
                hiddenDialog.show()
            }
        }

        btnAboutLauncher.setOnClickListener {
            dismiss()
            AboutDialog(context, preferences).show()
        }

        btnSystemSettings.setOnClickListener {
            dismiss()
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }

        btnModeDark.requestFocus()
    }
}
