package com.andsmarttv.launcher.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andsmarttv.launcher.R
import com.andsmarttv.launcher.data.AppDiscoveryManager
import com.andsmarttv.launcher.data.LauncherPreferences
import com.andsmarttv.launcher.data.model.AppInfo
import com.andsmarttv.launcher.receiver.BluetoothStateReceiver
import com.andsmarttv.launcher.receiver.NetworkStateReceiver
import com.andsmarttv.launcher.receiver.NetworkType
import com.andsmarttv.launcher.receiver.OtgStorageReceiver
import com.andsmarttv.launcher.receiver.PackageChangeReceiver
import com.andsmarttv.launcher.ui.adapter.AppGridAdapter
import com.andsmarttv.launcher.ui.adapter.PageTransitionDirection
import com.andsmarttv.launcher.ui.adapter.FavoritesAdapter
import com.andsmarttv.launcher.ui.dialog.AppOptionsDialog
import com.andsmarttv.launcher.ui.dialog.SettingsOverlayDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var preferences: LauncherPreferences
    private lateinit var discoveryManager: AppDiscoveryManager
    private lateinit var gridAdapter: AppGridAdapter
    private lateinit var favoritesAdapter: FavoritesAdapter

    // UI Elements
    private lateinit var rootContainer: View
    private lateinit var viewStatusBar: View
    private lateinit var ivFavoritesDockStar: ImageView
    private lateinit var layoutFavoritesSection: View
    private lateinit var layoutFavoritesEmpty: View
    private lateinit var tvFavoritesEmpty: TextView
    private lateinit var layoutAppsHeader: LinearLayout
    private lateinit var layoutAppsSection: View
    private lateinit var layoutPageDots: LinearLayout
    private lateinit var rvFavorites: RecyclerView
    private lateinit var rvApps: RecyclerView
    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvClockDivider: TextView
    private lateinit var ivAppsSectionIcon: ImageView
    private lateinit var ivWifiStatus: ImageView
    private lateinit var ivBluetoothStatus: ImageView
    private lateinit var ivVpnStatus: ImageView
    private lateinit var ivUsbStatus: ImageView
    private lateinit var ivSettingsBtn: ImageView

    // Data & State
    private var allAppsList: List<AppInfo> = emptyList()
    private var isAppsSectionActive: Boolean = false
    private var isBluetoothEnabled: Boolean = false
    private var pendingCustomBannerKey: String? = null

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val selectedUri: Uri? = result.data?.data
                val appKey = pendingCustomBannerKey
                if (selectedUri != null && appKey != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            selectedUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Ignore if not persistable
                    }
                    activityScope.launch(Dispatchers.IO) {
                        preferences.saveCustomBanner(appKey, selectedUri)
                        withContext(Dispatchers.Main) {
                            loadApps()
                        }
                    }
                }
            }
        }

    // Receivers
    private var packageReceiver: PackageChangeReceiver? = null
    private var networkReceiver: NetworkStateReceiver? = null
    private var otgReceiver: OtgStorageReceiver? = null
    private var bluetoothReceiver: BluetoothStateReceiver? = null
    private var timeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = LauncherPreferences(this)
        discoveryManager = AppDiscoveryManager(this, preferences)

        initViews()
        applyTheme()
        initReceivers()
        loadApps()
    }

    private fun initViews() {
        rootContainer = findViewById(R.id.rootContainer)
        viewStatusBar = findViewById(R.id.viewStatusBar)
        ivFavoritesDockStar = findViewById(R.id.ivFavoritesDockStar)
        layoutFavoritesSection = findViewById(R.id.layoutFavoritesSection)
        layoutFavoritesEmpty = findViewById(R.id.layoutFavoritesEmpty)
        tvFavoritesEmpty = findViewById(R.id.tvFavoritesEmpty)
        layoutAppsHeader = findViewById(R.id.layoutAppsHeader)
        layoutAppsSection = findViewById(R.id.layoutAppsSection)
        layoutPageDots = findViewById(R.id.layoutPageDots)
        rvFavorites = findViewById(R.id.rvFavorites)
        rvApps = findViewById(R.id.rvApps)
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        tvClockDivider = findViewById(R.id.tvClockDivider)
        ivAppsSectionIcon = findViewById(R.id.ivAppsSectionIcon)
        ivWifiStatus = findViewById(R.id.ivWifiStatus)
        ivBluetoothStatus = findViewById(R.id.ivBluetoothStatus)
        ivVpnStatus = findViewById(R.id.ivVpnStatus)
        ivUsbStatus = findViewById(R.id.ivUsbStatus)
        ivSettingsBtn = findViewById(R.id.ivSettingsBtn)

        // Status Bar Click Listeners
        ivWifiStatus.setOnClickListener {
            openSettingsPage(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
        }
        ivBluetoothStatus.setOnClickListener {
            openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        ivVpnStatus.setOnClickListener {
            openSettingsPage(Settings.ACTION_VPN_SETTINGS, "android.net.vpn.SETTINGS")
        }
        ivSettingsBtn.setOnClickListener {
            showSettings()
        }

        // Status Bar Focus Listeners (Consistent icon highlight when selected with remote)
        val statusButtons = listOf(ivWifiStatus, ivBluetoothStatus, ivVpnStatus, ivSettingsBtn)
        for (btn in statusButtons) {
            btn.setOnFocusChangeListener { _, _ ->
                updateStatusBarIconsTheme()
            }
        }

        // Empty favorites state interactions
        layoutFavoritesEmpty.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        showAppsSection()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        ivSettingsBtn.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
        layoutFavoritesEmpty.setOnClickListener {
            showAppsSection()
        }

        updateTimeDisplay()

        val columns = preferences.getGridColumns()

        // 1. Setup Favorites Grid (Fixed size & caching for zero-lag rendering)
        rvFavorites.layoutManager = GridLayoutManager(this, columns)
        rvFavorites.setHasFixedSize(true)
        rvFavorites.setItemViewCacheSize(10)
        rvFavorites.itemAnimator = null
        favoritesAdapter = FavoritesAdapter(
            context = this,
            isDarkMode = { preferences.isDarkMode() },
            onAppClicked = { app ->
                launchApp(app)
            },
            onAppOptionsRequested = { app, position ->
                showAppOptions(app, position, isFavorites = true)
            },
            onOrderChanged = { currentList ->
                val newOrder = currentList.map { it.uniqueKey }
                preferences.saveFavoritesOrder(newOrder)
            }
        )
        favoritesAdapter.onMoveModeChanged = { isMoving ->
            (viewStatusBar as? ViewGroup)?.descendantFocusability =
                if (isMoving) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        rvFavorites.adapter = favoritesAdapter

        // 2. Setup Main Apps Grid (Fixed size & caching for zero-lag rendering)
        rvApps.layoutManager = GridLayoutManager(this, columns)
        rvApps.setHasFixedSize(true)
        rvApps.setItemViewCacheSize(24)
        rvApps.itemAnimator = null
        gridAdapter = AppGridAdapter(
            context = this,
            isDarkMode = { preferences.isDarkMode() },
            getColumns = { preferences.getGridColumns() },
            onAppClicked = { app ->
                launchApp(app)
            },
            onAppOptionsRequested = { app, position ->
                showAppOptions(app, position, isFavorites = false)
            },
            onOrderChanged = { currentList ->
                val newOrder = currentList.map { it.uniqueKey }
                preferences.saveAppOrder(newOrder)
            },
            onPageChanged = { currentPage, totalPages ->
                updatePageDots(currentPage, totalPages)
            },
            onRequestFocusLock = { lock ->
                (viewStatusBar as? ViewGroup)?.descendantFocusability =
                    if (lock) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
        )
        gridAdapter.onMoveModeChanged = { isMoving ->
            (viewStatusBar as? ViewGroup)?.descendantFocusability =
                if (isMoving) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        rvApps.adapter = gridAdapter

        // Initial stage state: Favorites dock visible at bottom/hero area
        layoutFavoritesSection.visibility = View.VISIBLE
        ivFavoritesDockStar.visibility = View.VISIBLE
        layoutAppsSection.visibility = View.GONE
        layoutAppsHeader.visibility = View.GONE
        isAppsSectionActive = false
    }

    private fun updatePageDots(currentPage: Int, totalPages: Int) {
        if (totalPages <= 1) {
            layoutPageDots.visibility = View.GONE
            return
        }
        layoutPageDots.visibility = View.VISIBLE
        layoutPageDots.removeAllViews()
        val isDark = preferences.isDarkMode()

        for (i in 0 until totalPages) {
            val dot = ImageView(this).apply {
                val size = (6 * resources.displayMetrics.density).toInt()
                val margin = (3 * resources.displayMetrics.density).toInt()
                val params = LinearLayout.LayoutParams(size, size).apply {
                    leftMargin = margin
                    rightMargin = margin
                }
                layoutParams = params
                setImageResource(
                    if (i == currentPage) {
                        R.drawable.ic_page_dot_active
                    } else {
                        if (isDark) R.drawable.ic_page_dot_inactive else R.drawable.ic_page_dot_inactive_light
                    }
                )
            }
            layoutPageDots.addView(dot)
        }
    }

    private fun showAppsSection() {
        if (isAppsSectionActive) return
        isAppsSectionActive = true

        // Prevent focus hopping to status bar during view swap
        (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        ivFavoritesDockStar.animate()
            .translationY(80f)
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                ivFavoritesDockStar.visibility = View.GONE
            }
            .start()

        layoutFavoritesSection.animate()
            .translationY(80f)
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                layoutFavoritesSection.visibility = View.GONE
            }
            .start()

        gridAdapter.resetToFirstPage(rvApps)

        layoutAppsHeader.visibility = View.VISIBLE
        layoutAppsHeader.translationY = 80f
        layoutAppsHeader.alpha = 0f
        layoutAppsHeader.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(180)
            .start()

        layoutAppsSection.visibility = View.VISIBLE
        layoutAppsSection.translationY = 80f
        layoutAppsSection.alpha = 0f
        layoutAppsSection.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(180)
            .withEndAction {
                rvApps.post {
                    val vh = rvApps.findViewHolderForAdapterPosition(0)
                    vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                        ?: vh?.itemView?.requestFocus()
                        ?: rvApps.requestFocus()
                    (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                }
            }
            .start()
    }

    private fun showFavoritesSection() {
        if (!isAppsSectionActive) return
        isAppsSectionActive = false

        // Prevent focus hopping to status bar during view swap
        (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        layoutAppsHeader.animate()
            .translationY(80f)
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                layoutAppsHeader.visibility = View.GONE
            }
            .start()

        layoutAppsSection.animate()
            .translationY(80f)
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                layoutAppsSection.visibility = View.GONE
            }
            .start()

        ivFavoritesDockStar.visibility = View.VISIBLE
        ivFavoritesDockStar.translationY = 80f
        ivFavoritesDockStar.alpha = 0f
        ivFavoritesDockStar.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(180)
            .start()

        layoutFavoritesSection.visibility = View.VISIBLE
        layoutFavoritesSection.translationY = 80f
        layoutFavoritesSection.alpha = 0f
        layoutFavoritesSection.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(180)
            .withEndAction {
                if (favoritesAdapter.itemCount > 0) {
                    rvFavorites.post {
                        val vh = rvFavorites.findViewHolderForAdapterPosition(0)
                        vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                            ?: vh?.itemView?.requestFocus()
                            ?: rvFavorites.requestFocus()
                        (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    }
                } else {
                    layoutFavoritesEmpty.requestFocus()
                    (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                }
            }
            .start()
    }

    private fun updateStatusBarIconsTheme() {
        val isDark = preferences.isDarkMode()
        val primaryColor = if (isDark) Color.WHITE else Color.parseColor("#0F172A")
        val secondaryColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        val accentColor = if (isDark) ContextCompat.getColor(this, R.color.accent_cyan) else Color.parseColor("#2563EB")

        // Wi-Fi icon
        ivWifiStatus.setColorFilter(if (ivWifiStatus.isFocused) Color.WHITE else primaryColor)

        // Bluetooth icon (uses legible secondaryColor when off in both dark & light mode!)
        if (ivBluetoothStatus.isFocused) {
            ivBluetoothStatus.setColorFilter(Color.WHITE)
        } else {
            if (isBluetoothEnabled) {
                ivBluetoothStatus.setColorFilter(accentColor)
            } else {
                ivBluetoothStatus.setColorFilter(secondaryColor)
            }
        }

        // VPN icon
        ivVpnStatus.setColorFilter(if (ivVpnStatus.isFocused) Color.WHITE else accentColor)

        // USB icon
        ivUsbStatus.setColorFilter(accentColor)

        // Settings icon (pure white if focused, else primary theme color)
        ivSettingsBtn.setColorFilter(if (ivSettingsBtn.isFocused) Color.WHITE else primaryColor)
    }

    private fun applyTheme() {
        val isDark = preferences.isDarkMode()
        val themeIndex = preferences.getThemeIndex()
        val themeRes = if (!isDark) {
            tvClock.setTextColor(Color.parseColor("#0F172A"))
            tvDate.setTextColor(Color.parseColor("#334155"))
            tvClockDivider.setTextColor(Color.parseColor("#64748B"))
            ivAppsSectionIcon.setColorFilter(Color.parseColor("#2563EB"))
            tvFavoritesEmpty.setTextColor(Color.parseColor("#64748B"))
            when (themeIndex) {
                LauncherPreferences.THEME_SUNSET -> R.drawable.bg_theme_light_sunset
                LauncherPreferences.THEME_EMERALD -> R.drawable.bg_theme_light_emerald
                LauncherPreferences.THEME_PURPLE -> R.drawable.bg_theme_light_purple
                else -> R.drawable.bg_theme_light_midnight
            }
        } else {
            tvClock.setTextColor(Color.parseColor("#FFFFFF"))
            tvDate.setTextColor(Color.parseColor("#94A3B8"))
            tvClockDivider.setTextColor(Color.parseColor("#64748B"))
            ivAppsSectionIcon.setColorFilter(Color.parseColor("#38BDF8"))
            tvFavoritesEmpty.setTextColor(Color.parseColor("#94A3B8"))
            when (themeIndex) {
                LauncherPreferences.THEME_SUNSET -> R.drawable.bg_theme_sunset
                LauncherPreferences.THEME_EMERALD -> R.drawable.bg_theme_emerald
                LauncherPreferences.THEME_PURPLE -> R.drawable.bg_theme_purple
                LauncherPreferences.THEME_MONET_BLUE -> R.drawable.bg_theme_monet_blue
                else -> R.drawable.bg_theme_midnight
            }
        }
        rootContainer.setBackgroundResource(themeRes)

        val dockBg = if (isDark) R.drawable.bg_glass_dock_prominent else R.drawable.bg_glass_dock_light
        viewStatusBar.setBackgroundResource(dockBg)
        layoutFavoritesSection.setBackgroundResource(dockBg)
        layoutAppsSection.setBackgroundResource(dockBg)

        updateStatusBarIconsTheme()
    }

    private fun loadApps(
        preservePage: Int? = null,
        targetFocusPosition: Int? = null,
        onComplete: (() -> Unit)? = null
    ) {
        activityScope.launch {
            var apps = discoveryManager.loadInstalledApps(includeHidden = false)

            if (apps.isEmpty()) {
                preferences.unhideAll()
                apps = discoveryManager.loadInstalledApps(includeHidden = false)
            }

            val favOrderMap = preferences.getFavoritesOrder().withIndex().associate { it.value to it.index }
            val appOrderMap = preferences.getAppOrder().withIndex().associate { it.value to it.index }

            val favApps = apps.filter { it.isFavorite }.sortedWith(Comparator { a1, a2 ->
                val p1 = favOrderMap[a1.uniqueKey] ?: (Int.MAX_VALUE - 1000)
                val p2 = favOrderMap[a2.uniqueKey] ?: (Int.MAX_VALUE - 1000)
                if (p1 != p2) p1.compareTo(p2) else a1.label.compareTo(a2.label, ignoreCase = true)
            })

            val regularApps = apps.filter { !it.isFavorite }.sortedWith(Comparator { a1, a2 ->
                val p1 = appOrderMap[a1.uniqueKey] ?: (Int.MAX_VALUE - 1000)
                val p2 = appOrderMap[a2.uniqueKey] ?: (Int.MAX_VALUE - 1000)
                if (p1 != p2) p1.compareTo(p2) else a1.label.compareTo(a2.label, ignoreCase = true)
            })

            if (favApps.isEmpty()) {
                rvFavorites.visibility = View.GONE
                layoutFavoritesEmpty.visibility = View.VISIBLE
            } else {
                rvFavorites.visibility = View.VISIBLE
                layoutFavoritesEmpty.visibility = View.GONE
                favoritesAdapter.submitList(favApps)
            }

            gridAdapter.submitList(regularApps, preservePage, targetFocusPosition)
            onComplete?.invoke()
        }
    }

    private fun launchApp(app: AppInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
    }

    private fun showAppOptions(app: AppInfo, position: Int, isFavorites: Boolean = false) {
        val targetRv = if (isFavorites) rvFavorites else rvApps
        val dialog = AppOptionsDialog(
            context = this,
            app = app,
            position = position,
            preferences = preferences,
            onMoveSelected = { pos ->
                (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                if (isFavorites) {
                    favoritesAdapter.startMoveMode(pos, rvFavorites)
                } else {
                    gridAdapter.startMoveMode(pos, rvApps)
                }
            },
            onHideSelected = { appToHide ->
                val savedPage = if (isFavorites) 0 else gridAdapter.currentPage
                (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                preferences.setAppHidden(appToHide.uniqueKey, true)

                val targetLocalPos = if (isFavorites) {
                    val count = favoritesAdapter.itemCount
                    if (position >= count - 1) (count - 2).coerceAtLeast(0) else position
                } else {
                    val batchSize = gridAdapter.currentBatch.size
                    val local = position % gridAdapter.pageSize
                    if (local >= batchSize - 1) (local - 1).coerceAtLeast(0) else local
                }

                loadApps(preservePage = savedPage, targetFocusPosition = targetLocalPos) {
                    targetRv.post {
                        if (isFavorites) {
                            val itemCount = favoritesAdapter.itemCount
                            if (itemCount > 0) {
                                val targetPos = targetLocalPos.coerceIn(0, itemCount - 1)
                                rvFavorites.scrollToPosition(targetPos)
                                val vh = rvFavorites.findViewHolderForAdapterPosition(targetPos)
                                vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                                    ?: vh?.itemView?.requestFocus()
                                    ?: rvFavorites.requestFocus()
                                (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                            } else {
                                layoutFavoritesEmpty.requestFocus()
                                (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                            }
                        } else {
                            val totalPages = gridAdapter.totalPages
                            val targetPage = savedPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                            val pageBatchSize = gridAdapter.currentBatch.size
                            val safeLocalPos = targetLocalPos.coerceIn(0, (pageBatchSize - 1).coerceAtLeast(0))
                            updatePageDots(targetPage, totalPages)
                            val vh = rvApps.findViewHolderForAdapterPosition(safeLocalPos)
                            vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                                ?: vh?.itemView?.requestFocus()
                                ?: rvApps.requestFocus()
                            (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        }
                    }
                }
            },
            onCustomBannerRequested = { appToChange ->
                pendingCustomBannerKey = appToChange.uniqueKey
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                    imagePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Intent.ACTION_PICK).apply {
                        type = "image/*"
                    }
                    imagePickerLauncher.launch(fallbackIntent)
                }
            },
            onAppUpdated = {
                (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                val savedPage = if (isFavorites) 0 else gridAdapter.currentPage
                loadApps(preservePage = savedPage) {
                    targetRv.post {
                        val adapter = targetRv.adapter
                        val itemCount = adapter?.itemCount ?: 0
                        if (itemCount > 0) {
                            val targetPos = position.coerceIn(0, itemCount - 1)
                            targetRv.scrollToPosition(targetPos)
                            targetRv.postDelayed({
                                val vh = targetRv.findViewHolderForAdapterPosition(targetPos)
                                vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                                    ?: vh?.itemView?.requestFocus()
                                    ?: targetRv.requestFocus()
                                (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                            }, 50)
                        } else {
                            if (isFavorites) layoutFavoritesEmpty.requestFocus()
                            (viewStatusBar as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        }
                    }
                }
            }
        )
        dialog.show()
    }

    private fun showSettings() {
        val dialog = SettingsOverlayDialog(
            context = this,
            preferences = preferences,
            discoveryManager = discoveryManager,
            onSettingsChanged = {
                applyTheme()
                val newCols = preferences.getGridColumns()
                (rvFavorites.layoutManager as? GridLayoutManager)?.spanCount = newCols
                (rvApps.layoutManager as? GridLayoutManager)?.spanCount = newCols
                favoritesAdapter.notifyDataSetChanged()
                gridAdapter.notifyDataSetChanged()
                loadApps()
            }
        )
        dialog.show()
    }

    private fun openSettingsPage(action: String, fallbackAction: String? = null) {
        try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            if (fallbackAction != null) {
                try {
                    val fallbackIntent = Intent(fallbackAction).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    openGeneralSettings()
                }
            } else {
                openGeneralSettings()
            }
        }
    }

    private fun openGeneralSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            try {
                val tvIntent = Intent().apply {
                    component = ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(tvIntent)
            } catch (e2: Exception) {
                // Ignore if unavailable
            }
        }
    }

    private fun initReceivers() {
        // 1. Package changes
        packageReceiver = PackageChangeReceiver {
            loadApps()
        }.also { it.register(this) }

        // 2. Network / WiFi / VPN
        networkReceiver = NetworkStateReceiver { type, isVpn ->
            updateNetworkIcon(type)
            ivVpnStatus.visibility = if (isVpn) View.VISIBLE else View.GONE
            updateStatusBarIconsTheme()
        }.also { it.register(this) }

        // 3. OTG / USB storage
        otgReceiver = OtgStorageReceiver { isMounted ->
            ivUsbStatus.visibility = if (isMounted) View.VISIBLE else View.GONE
            updateStatusBarIconsTheme()
        }.also { it.register(this) }

        // 4. Bluetooth status
        bluetoothReceiver = BluetoothStateReceiver { isEnabled ->
            isBluetoothEnabled = isEnabled
            ivBluetoothStatus.setImageResource(
                if (isEnabled) R.drawable.ic_bluetooth_on else R.drawable.ic_bluetooth_off
            )
            updateStatusBarIconsTheme()
        }.also { it.register(this) }

        // 5. Time tick
        timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateTimeDisplay()
            }
        }
        val timeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeReceiver, timeFilter)
    }

    private fun updateTimeDisplay() {
        val now = Date()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        tvClock.text = timeFormat.format(now)
        tvDate.text = dateFormat.format(now)
    }

    private fun updateNetworkIcon(type: NetworkType) {
        when (type) {
            NetworkType.NONE -> {
                ivWifiStatus.setImageResource(R.drawable.ic_wifi_off)
                ivWifiStatus.setColorFilter(ContextCompat.getColor(this, R.color.status_inactive))
            }
            NetworkType.WIFI_LOW -> {
                ivWifiStatus.setImageResource(R.drawable.ic_wifi_low)
                ivWifiStatus.setColorFilter(ContextCompat.getColor(this, R.color.status_active))
            }
            NetworkType.WIFI_MEDIUM -> {
                ivWifiStatus.setImageResource(R.drawable.ic_wifi_med)
                ivWifiStatus.setColorFilter(ContextCompat.getColor(this, R.color.status_active))
            }
            NetworkType.WIFI_HIGH,
            NetworkType.WIFI_FULL -> {
                ivWifiStatus.setImageResource(R.drawable.ic_wifi_full)
                ivWifiStatus.setColorFilter(ContextCompat.getColor(this, R.color.status_active))
            }
            NetworkType.ETHERNET -> {
                ivWifiStatus.setImageResource(R.drawable.ic_ethernet)
                ivWifiStatus.setColorFilter(ContextCompat.getColor(this, R.color.status_active))
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Stage 1 -> Stage 2: Pressing DOWN when on Favorites dock
            if (!isAppsSectionActive && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val focused = currentFocus
                if (focused == null || isChildOf(focused, layoutFavoritesSection) || isChildOf(focused, rvFavorites) || isChildOf(focused, layoutFavoritesEmpty)) {
                    showAppsSection()
                    return true
                }
            }

            // Status Bar -> Active Dock on DPAD_DOWN
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val focused = currentFocus
                if (focused != null && isChildOf(focused, viewStatusBar)) {
                    if (isAppsSectionActive) {
                        rvApps.post {
                            val vh = rvApps.findViewHolderForAdapterPosition(0)
                            vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                                ?: vh?.itemView?.requestFocus()
                        }
                    } else {
                        if (favoritesAdapter.itemCount > 0) {
                            rvFavorites.post {
                                val vh = rvFavorites.findViewHolderForAdapterPosition(0)
                                vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                                    ?: vh?.itemView?.requestFocus()
                            }
                        } else {
                            layoutFavoritesEmpty.requestFocus()
                        }
                    }
                    return true
                }
            }

            // Stage 2 -> Stage 1: Pressing UP when on top row of Page 0 in Apps grid
            if (isAppsSectionActive && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val focused = currentFocus
                if (focused != null && isChildOf(focused, rvApps)) {
                    val holder = rvApps.findContainingViewHolder(focused)
                    val pos = holder?.bindingAdapterPosition ?: 0
                    val cols = preferences.getGridColumns()
                    if (gridAdapter.currentPage == 0 && pos < cols) {
                        showFavoritesSection()
                        return true
                    }
                }
            }

            // Back button
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (favoritesAdapter.isMoveMode) {
                    favoritesAdapter.stopMoveMode(rvFavorites)
                    return true
                }
                if (gridAdapter.isMoveMode) {
                    gridAdapter.stopMoveMode(rvApps)
                    return true
                }
                if (isAppsSectionActive) {
                    showFavoritesSection()
                    return true
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isChildOf(view: View, parent: View): Boolean {
        var v: View? = view
        while (v != null) {
            if (v == parent) return true
            v = v.parent as? View
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        packageReceiver?.unregister(this)
        networkReceiver?.unregister(this)
        otgReceiver?.unregister(this)
        bluetoothReceiver?.unregister(this)
        timeReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {}
        }
    }
}
