package com.andsmarttv.launcher.ui.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.core.widget.TextViewCompat
import com.andsmarttv.launcher.R
import com.andsmarttv.launcher.data.LauncherPreferences
import com.andsmarttv.launcher.data.model.AppInfo

class AppOptionsDialog(
    context: Context,
    private val app: AppInfo,
    private val position: Int,
    private val preferences: LauncherPreferences,
    private val onMoveSelected: (Int) -> Unit,
    private val onHideSelected: (AppInfo) -> Unit,
    private val onCustomBannerRequested: (AppInfo) -> Unit,
    private val onAppUpdated: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_app_options)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val dialogContainer: View = findViewById(R.id.dialogContainer)
        val ivAppIcon: ImageView = findViewById(R.id.ivAppIcon)
        val tvAppTitle: TextView = findViewById(R.id.tvAppTitle)
        val tvPackageName: TextView = findViewById(R.id.tvPackageName)
        val divOptions: View = findViewById(R.id.divOptions)
        val btnFavorite: Button = findViewById(R.id.btnFavorite)
        val btnChangeBanner: Button = findViewById(R.id.btnChangeBanner)
        val btnMove: Button = findViewById(R.id.btnMove)
        val btnHide: Button = findViewById(R.id.btnHide)
        val btnAppInfo: Button = findViewById(R.id.btnAppInfo)
        val btnUninstall: Button = findViewById(R.id.btnUninstall)

        // Apply Adaptive Theming
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
        val btnSelector = if (isDark) R.drawable.btn_dialog_solid_selector else R.drawable.btn_dialog_light_selector

        tvAppTitle.setTextColor(primaryTextColor)
        tvPackageName.setTextColor(secondaryTextColor)
        divOptions.setBackgroundColor(dividerColor)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf()
        )
        val textColors = if (isDark) {
            intArrayOf(
                Color.parseColor("#FFFFFF"),
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
                Color.parseColor("#FFFFFF"),
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

        val standardButtons = listOf(btnFavorite, btnChangeBanner, btnMove, btnHide, btnAppInfo)
        standardButtons.forEach { btn ->
            btn.stateListAnimator = null
            btn.elevation = 0f
            btn.setBackgroundResource(btnSelector)
            btn.setTextColor(textColorList)
            TextViewCompat.setCompoundDrawableTintList(btn, iconColorList)
        }

        // Danger Uninstall Button
        val dangerTextColors = if (isDark) {
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#FFFFFF")
            )
        } else {
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#DC2626")
            )
        }
        val dangerTextColorList = ColorStateList(states, dangerTextColors)
        btnUninstall.stateListAnimator = null
        btnUninstall.elevation = 0f
        btnUninstall.setTextColor(dangerTextColorList)
        TextViewCompat.setCompoundDrawableTintList(btnUninstall, dangerTextColorList)

        // Header info
        tvAppTitle.text = app.label
        tvPackageName.text = app.packageName
        if (app.iconDrawable != null) {
            ivAppIcon.setImageDrawable(app.iconDrawable)
        }

        // Favorite Button label
        val isFav = preferences.isFavorite(app.uniqueKey)
        val favCount = preferences.getFavorites().size
        if (isFav) {
            btnFavorite.text = "Remove from Favorites"
        } else if (favCount >= LauncherPreferences.MAX_FAVORITES) {
            btnFavorite.text = "Favorites Full (Max 4)"
        } else {
            btnFavorite.text = "Add to Favorites"
        }

        btnFavorite.setOnClickListener {
            if (!isFav && preferences.getFavorites().size >= LauncherPreferences.MAX_FAVORITES) {
                android.widget.Toast.makeText(context, "Favorites is full (Maximum 4 apps allowed)", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newState = preferences.toggleFavorite(app.uniqueKey)
            app.isFavorite = newState
            dismiss()
            onAppUpdated.invoke()
        }

        btnChangeBanner.setOnClickListener {
            dismiss()
            onCustomBannerRequested.invoke(app)
        }

        btnMove.setOnClickListener {
            onMoveSelected.invoke(position)
            dismiss()
        }

        btnHide.setOnClickListener {
            onHideSelected.invoke(app)
            dismiss()
        }

        btnAppInfo.setOnClickListener {
            dismiss()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }

        btnUninstall.setOnClickListener {
            dismiss()
            try {
                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallback = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.fromParts("package", app.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }

        btnFavorite.requestFocus()
    }
}
