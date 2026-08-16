package com.andsmarttv.launcher.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andsmarttv.launcher.R
import com.andsmarttv.launcher.data.LauncherPreferences
import com.andsmarttv.launcher.data.model.AppInfo

class HiddenAppsDialog(
    context: Context,
    private val allHiddenApps: List<AppInfo>,
    private val preferences: LauncherPreferences,
    private val onAppsChanged: () -> Unit
) : Dialog(context) {

    private val currentHiddenList = allHiddenApps.toMutableList()
    private lateinit var adapter: HiddenAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_hidden_apps)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val dialogContainer: View = findViewById(R.id.dialogContainer)
        val tvHiddenTitle: TextView = findViewById(R.id.tvHiddenTitle)
        val tvEmptyHidden: TextView = findViewById(R.id.tvEmptyHidden)
        val divHidden1: View = findViewById(R.id.divHidden1)
        val divHidden2: View = findViewById(R.id.divHidden2)
        val rvHiddenApps: RecyclerView = findViewById(R.id.rvHiddenApps)
        val btnUnhideAll: Button = findViewById(R.id.btnUnhideAll)
        val btnCloseHidden: Button = findViewById(R.id.btnCloseHidden)

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

        tvHiddenTitle.setTextColor(primaryTextColor)
        tvEmptyHidden.setTextColor(secondaryTextColor)
        divHidden1.setBackgroundColor(dividerColor)
        divHidden2.setBackgroundColor(dividerColor)

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

        btnUnhideAll.setBackgroundResource(btnSelector)
        btnUnhideAll.setTextColor(textColorList)
        TextViewCompat.setCompoundDrawableTintList(btnUnhideAll, iconColorList)

        btnCloseHidden.setBackgroundResource(btnSelector)
        btnCloseHidden.setTextColor(textColorList)
        TextViewCompat.setCompoundDrawableTintList(btnCloseHidden, iconColorList)

        rvHiddenApps.layoutManager = LinearLayoutManager(context)
        adapter = HiddenAppsAdapter(currentHiddenList, isDark, primaryTextColor, secondaryTextColor, btnSelector, textColorList, iconColorList) { app ->
            // Unhide this individual app
            preferences.setAppHidden(app.uniqueKey, false)
            currentHiddenList.remove(app)
            adapter.notifyDataSetChanged()
            updateVisibility(tvEmptyHidden, rvHiddenApps, btnUnhideAll)
            onAppsChanged.invoke()
        }
        rvHiddenApps.adapter = adapter

        updateVisibility(tvEmptyHidden, rvHiddenApps, btnUnhideAll)

        btnUnhideAll.setOnClickListener {
            currentHiddenList.forEach { app ->
                preferences.setAppHidden(app.uniqueKey, false)
            }
            currentHiddenList.clear()
            adapter.notifyDataSetChanged()
            updateVisibility(tvEmptyHidden, rvHiddenApps, btnUnhideAll)
            onAppsChanged.invoke()
        }

        btnCloseHidden.setOnClickListener {
            dismiss()
        }

        if (currentHiddenList.isNotEmpty()) {
            rvHiddenApps.requestFocus()
        } else {
            btnCloseHidden.requestFocus()
        }
    }

    private fun updateVisibility(emptyView: View, recyclerView: View, unhideAllBtn: View) {
        if (currentHiddenList.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            unhideAllBtn.isEnabled = false
            unhideAllBtn.alpha = 0.5f
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            unhideAllBtn.isEnabled = true
            unhideAllBtn.alpha = 1.0f
        }
    }

    private class HiddenAppsAdapter(
        private val list: List<AppInfo>,
        private val isDark: Boolean,
        private val primaryTextColor: Int,
        private val secondaryTextColor: Int,
        private val btnSelector: Int,
        private val textColorList: ColorStateList,
        private val iconColorList: ColorStateList,
        private val onUnhideClicked: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<HiddenAppsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hidden_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = list[position]
            holder.tvLabel.text = app.label
            holder.tvLabel.setTextColor(primaryTextColor)
            holder.tvPackage.text = app.packageName
            holder.tvPackage.setTextColor(secondaryTextColor)
            if (app.iconDrawable != null) {
                holder.ivIcon.setImageDrawable(app.iconDrawable)
            }
            holder.btnUnhide.setBackgroundResource(btnSelector)
            holder.btnUnhide.setTextColor(textColorList)
            TextViewCompat.setCompoundDrawableTintList(holder.btnUnhide, iconColorList)
            holder.btnUnhide.setOnClickListener {
                onUnhideClicked.invoke(app)
            }
        }

        override fun getItemCount(): Int = list.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivIcon: ImageView = itemView.findViewById(R.id.ivHiddenAppIcon)
            val tvLabel: TextView = itemView.findViewById(R.id.tvHiddenAppName)
            val tvPackage: TextView = itemView.findViewById(R.id.tvHiddenAppPkg)
            val btnUnhide: Button = itemView.findViewById(R.id.btnUnhideApp)
        }
    }
}
