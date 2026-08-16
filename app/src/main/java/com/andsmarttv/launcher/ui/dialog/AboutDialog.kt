package com.andsmarttv.launcher.ui.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.content.res.ColorStateList
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.andsmarttv.launcher.R
import com.andsmarttv.launcher.data.LauncherPreferences

class AboutDialog(
    context: Context,
    private val preferences: LauncherPreferences
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_about)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val dialogContainer: View = findViewById(R.id.dialogContainer)
        val tvAboutTitle: TextView = findViewById(R.id.tvAboutTitle)
        val tvAppVersion: TextView = findViewById(R.id.tvAppVersion)
        val tvAboutDescription: TextView = findViewById(R.id.tvAboutDescription)
        val divAbout: View = findViewById(R.id.divAbout)
        val layoutGithubLink: View = findViewById(R.id.layoutGithubLink)
        val tvDevName: TextView = findViewById(R.id.tvDevName)
        val tvGithubLink: TextView = findViewById(R.id.tvGithubLink)
        val tvOpenLink: TextView = findViewById(R.id.tvOpenLink)
        val btnClose: Button = findViewById(R.id.btnClose)
        val ivGithubIcon: ImageView = findViewById(R.id.ivGithubIcon)

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

        tvAboutTitle.setTextColor(primaryTextColor)
        tvAboutDescription.setTextColor(secondaryTextColor)
        tvDevName.setTextColor(primaryTextColor)
        tvOpenLink.setTextColor(primaryTextColor)
        divAbout.setBackgroundColor(dividerColor)

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
        val textColorList = ColorStateList(states, textColors)

        layoutGithubLink.setBackgroundResource(btnSelector)
        ivGithubIcon.setColorFilter(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#0F172A"))
        tvGithubLink.setTextColor(if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7"))

        layoutGithubLink.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                tvDevName.setTextColor(Color.WHITE)
                tvGithubLink.setTextColor(Color.parseColor("#BAE6FD"))
                tvOpenLink.setTextColor(Color.WHITE)
                ivGithubIcon.setColorFilter(Color.WHITE)
            } else {
                tvDevName.setTextColor(primaryTextColor)
                tvGithubLink.setTextColor(if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7"))
                tvOpenLink.setTextColor(primaryTextColor)
                ivGithubIcon.setColorFilter(if (isDark) Color.WHITE else Color.parseColor("#0F172A"))
            }
        }

        btnClose.stateListAnimator = null
        btnClose.elevation = 0f
        btnClose.setBackgroundResource(btnSelector)
        btnClose.setTextColor(textColorList)

        // Fetch version from package manager
        val versionName = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.5.2"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.5.2"
        }
        tvAppVersion.text = "v$versionName (Build 9) • Android Nougat Leanback"

        // Open developer GitHub in browser
        layoutGithubLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dnsalcedo01")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }

        btnClose.setOnClickListener {
            dismiss()
        }

        btnClose.requestFocus()
    }
}
