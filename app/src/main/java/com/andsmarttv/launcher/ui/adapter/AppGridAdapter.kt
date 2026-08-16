package com.andsmarttv.launcher.ui.adapter

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.andsmarttv.launcher.R
import com.andsmarttv.launcher.data.model.AppInfo
import com.andsmarttv.launcher.ui.view.FocusHighlightHelper

enum class PageTransitionDirection {
    NONE, DOWN, UP, LEFT, RIGHT
}

class AppGridAdapter(
    private val context: Context,
    private val isDarkMode: () -> Boolean,
    private val getColumns: () -> Int,
    private val onAppClicked: (AppInfo) -> Unit,
    private val onAppOptionsRequested: (AppInfo, Int) -> Unit,
    private val onOrderChanged: (List<AppInfo>) -> Unit,
    var onPageChanged: ((currentPage: Int, totalPages: Int) -> Unit)? = null,
    var onRequestFocusLock: ((Boolean) -> Unit)? = null,
    var onMoveModeChanged: ((Boolean) -> Unit)? = null
) : RecyclerView.Adapter<AppGridAdapter.AppViewHolder>() {

    companion object {
        private val COLOR_MOVE_ACTIVE = Color.parseColor("#3B82F6")
        private val COLOR_WHITE = Color.parseColor("#FFFFFF")
        private val COLOR_DARK_CARD = Color.parseColor("#1E293B")
        private val COLOR_DARK_CARD_TEXT = Color.parseColor("#FFFFFF")
        private val COLOR_DARK_SHADOW = Color.parseColor("#99000000")
        private val COLOR_LIGHT_CARD = Color.parseColor("#E2E8F0")
        private val COLOR_LIGHT_CARD_TEXT = Color.parseColor("#0F172A")
    }

    private val allApps = mutableListOf<AppInfo>()
    var currentPage = 0
        private set

    val pageSize: Int
        get() = getColumns.invoke() * 3

    val totalPages: Int
        get() = if (allApps.isEmpty()) 1 else ((allApps.size - 1) / pageSize) + 1

    val currentBatch: List<AppInfo>
        get() {
            val start = currentPage * pageSize
            val end = (start + pageSize).coerceAtMost(allApps.size)
            return if (start in 0 until allApps.size) allApps.subList(start, end) else emptyList()
        }

    var isMoveMode = false
        private set
    private var movingGlobalPosition: Int = RecyclerView.NO_POSITION
    var isPageTransitioning = false
        private set
    var pendingFocusPosition: Int? = null
        private set

    fun submitList(newList: List<AppInfo>, preservePage: Int? = null, targetFocusPosition: Int? = null) {
        allApps.clear()
        allApps.addAll(newList)
        val maxPage = if (totalPages > 0) totalPages - 1 else 0
        if (preservePage != null) {
            currentPage = preservePage.coerceIn(0, maxPage)
        } else {
            if (currentPage > maxPage) {
                currentPage = maxPage
            }
        }
        pendingFocusPosition = targetFocusPosition
        notifyDataSetChanged()
        onPageChanged?.invoke(currentPage, totalPages)
    }

    fun setPage(
        page: Int,
        targetLocalFocus: Int = 0,
        recyclerView: RecyclerView? = null,
        direction: PageTransitionDirection = PageTransitionDirection.NONE
    ) {
        val newPage = page.coerceIn(0, totalPages - 1)
        onRequestFocusLock?.invoke(true)

        if (recyclerView != null && direction != PageTransitionDirection.NONE && recyclerView.isAttachedToWindow) {
            isPageTransitioning = true
            val density = context.resources.displayMetrics.density
            val isVertical = direction == PageTransitionDirection.DOWN || direction == PageTransitionDirection.UP
            val exitOffset = when (direction) {
                PageTransitionDirection.DOWN -> -35f * density
                PageTransitionDirection.UP -> 35f * density
                PageTransitionDirection.LEFT -> 35f * density
                PageTransitionDirection.RIGHT -> -35f * density
                else -> 0f
            }
            val enterOffset = -exitOffset

            recyclerView.animate().cancel()
            val anim = if (isVertical) {
                recyclerView.animate().translationY(exitOffset)
            } else {
                recyclerView.animate().translationX(exitOffset)
            }

            anim.alpha(0.2f)
                .setDuration(80)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    currentPage = newPage
                    notifyDataSetChanged()
                    onPageChanged?.invoke(currentPage, totalPages)

                    if (isVertical) {
                        recyclerView.translationY = enterOffset
                        recyclerView.translationX = 0f
                    } else {
                        recyclerView.translationX = enterOffset
                        recyclerView.translationY = 0f
                    }
                    recyclerView.alpha = 0.2f

                    val safeFocus = targetLocalFocus.coerceIn(0, (currentBatch.size - 1).coerceAtLeast(0))
                    recyclerView.post {
                        val vh = recyclerView.findViewHolderForAdapterPosition(safeFocus)
                        vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                            ?: vh?.itemView?.requestFocus()

                        val enterAnim = if (isVertical) {
                            recyclerView.animate().translationY(0f)
                        } else {
                            recyclerView.animate().translationX(0f)
                        }

                        enterAnim.alpha(1f)
                            .setDuration(120)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .withEndAction {
                                isPageTransitioning = false
                                onRequestFocusLock?.invoke(false)
                            }
                            .start()
                    }
                }
                .start()
        } else {
            isPageTransitioning = false
            currentPage = newPage
            notifyDataSetChanged()
            onPageChanged?.invoke(currentPage, totalPages)
            if (recyclerView != null) {
                recyclerView.animate().cancel()
                recyclerView.translationX = 0f
                recyclerView.translationY = 0f
                recyclerView.alpha = 1f
                val safeFocus = targetLocalFocus.coerceIn(0, (currentBatch.size - 1).coerceAtLeast(0))
                recyclerView.post {
                    val vh = recyclerView.findViewHolderForAdapterPosition(safeFocus)
                    vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                        ?: vh?.itemView?.requestFocus()
                    onRequestFocusLock?.invoke(false)
                }
            } else {
                onRequestFocusLock?.invoke(false)
            }
        }
    }

    fun resetToFirstPage(recyclerView: RecyclerView? = null) {
        isPageTransitioning = false
        setPage(0, 0, recyclerView, PageTransitionDirection.NONE)
    }

    fun startMoveMode(globalPosition: Int, recyclerView: RecyclerView? = null) {
        if (globalPosition in 0 until allApps.size) {
            isMoveMode = true
            movingGlobalPosition = globalPosition
            onMoveModeChanged?.invoke(true)
            val page = globalPosition / pageSize
            val localPos = globalPosition % pageSize
            if (currentPage != page) {
                setPage(page, localPos, recyclerView, PageTransitionDirection.NONE)
            } else {
                notifyItemChanged(localPos)
                recyclerView?.post {
                    val vh = recyclerView.findViewHolderForAdapterPosition(localPos)
                    vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                        ?: vh?.itemView?.requestFocus()
                }
            }
        }
    }

    fun stopMoveMode(recyclerView: RecyclerView? = null) {
        if (isMoveMode) {
            val lastGlobalPos = movingGlobalPosition
            isMoveMode = false
            movingGlobalPosition = RecyclerView.NO_POSITION
            if (lastGlobalPos != RecyclerView.NO_POSITION) {
                val localPos = lastGlobalPos % pageSize
                notifyItemChanged(localPos)
            }
            onOrderChanged.invoke(allApps)

            if (lastGlobalPos != RecyclerView.NO_POSITION) {
                val localPos = lastGlobalPos % pageSize
                recyclerView?.post {
                    val vh = recyclerView.findViewHolderForAdapterPosition(localPos)
                    vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                        ?: vh?.itemView?.requestFocus()
                    onMoveModeChanged?.invoke(false)
                }
            } else {
                onMoveModeChanged?.invoke(false)
            }
        }
    }

    fun moveItem(fromGlobal: Int, toGlobal: Int, recyclerView: RecyclerView?) {
        if (fromGlobal in 0 until allApps.size && toGlobal in 0 until allApps.size) {
            val item = allApps.removeAt(fromGlobal)
            allApps.add(toGlobal, item)
            movingGlobalPosition = toGlobal
            val targetPage = toGlobal / pageSize
            val targetLocal = toGlobal % pageSize

            if (currentPage != targetPage) {
                val dir = if (toGlobal > fromGlobal) PageTransitionDirection.DOWN else PageTransitionDirection.UP
                setPage(targetPage, targetLocal, recyclerView, dir)
            } else {
                val fromLocal = fromGlobal % pageSize
                notifyItemMoved(fromLocal, targetLocal)
                notifyItemChanged(fromLocal)
                notifyItemChanged(targetLocal)
                recyclerView?.post {
                    val vh = recyclerView.findViewHolderForAdapterPosition(targetLocal)
                    vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                        ?: vh?.itemView?.requestFocus()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_card, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val globalPos = (currentPage * pageSize) + position
        holder.bind(currentBatch[position], position, globalPos)
    }

    override fun getItemCount(): Int = currentBatch.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardContainer: CardView = itemView.findViewById(R.id.cardContainer)
        private val ivBanner: ImageView = itemView.findViewById(R.id.ivBanner)
        private val layoutFallback: View = itemView.findViewById(R.id.layoutFallback)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)
        private val ivMoveBadge: ImageView = itemView.findViewById(R.id.ivMoveBadge)
        private val viewFocusGlow: View = itemView.findViewById(R.id.viewFocusGlow)

        private val longPressHandler = Handler(Looper.getMainLooper())
        private var isLongPressTriggered = false
        private var longPressRunnable: Runnable? = null

        init {
            FocusHighlightHelper.attachFocusAnimation(cardContainer, viewFocusGlow)

            // Touch / Mouse Gesture Listener
            cardContainer.setOnTouchListener { v, event ->
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos !in currentBatch.indices) return@setOnTouchListener false
                val globalPos = (currentPage * pageSize) + pos

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressTriggered = false
                        if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                            isLongPressTriggered = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onAppOptionsRequested.invoke(currentBatch[pos], globalPos)
                            return@setOnTouchListener true
                        }
                        longPressRunnable = Runnable {
                            val currentPos = bindingAdapterPosition
                            if (currentPos != RecyclerView.NO_POSITION && currentPos in currentBatch.indices) {
                                isLongPressTriggered = true
                                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                val currentGlobal = (currentPage * pageSize) + currentPos
                                onAppOptionsRequested.invoke(currentBatch[currentPos], currentGlobal)
                            }
                        }
                        longPressHandler.postDelayed(longPressRunnable!!, 450)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        if (!isLongPressTriggered) {
                            if (isMoveMode) {
                                val rv = (v.parent?.parent ?: v.parent) as? RecyclerView
                                stopMoveMode(rv)
                            } else if (pos in currentBatch.indices) {
                                onAppClicked.invoke(currentBatch[pos])
                            }
                        }
                        isLongPressTriggered = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        isLongPressTriggered = false
                        true
                    }
                    else -> false
                }
            }

            // D-Pad key handling for Long Press OK, Menu key, 2D Move Mode, and 2D Discrete Page Navigation
            cardContainer.setOnKeyListener { v, keyCode, event ->
                if (isPageTransitioning) return@setOnKeyListener true
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos !in currentBatch.indices) return@setOnKeyListener false
                val globalPos = (currentPage * pageSize) + pos
                val cols = getColumns.invoke()
                val rv = (v.parent?.parent ?: v.parent) as? RecyclerView

                if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
                    isLongPressTriggered = true
                    onAppOptionsRequested.invoke(currentBatch[pos], globalPos)
                    return@setOnKeyListener true
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (event.repeatCount == 0) {
                            isLongPressTriggered = false
                            longPressRunnable = Runnable {
                                val currentPos = bindingAdapterPosition
                                if (currentPos != RecyclerView.NO_POSITION && currentPos in currentBatch.indices) {
                                    isLongPressTriggered = true
                                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    val currentGlobal = (currentPage * pageSize) + currentPos
                                    onAppOptionsRequested.invoke(currentBatch[currentPos], currentGlobal)
                                }
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, 450)
                        }
                        return@setOnKeyListener true
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        if (isLongPressTriggered) {
                            isLongPressTriggered = false
                            return@setOnKeyListener true
                        }
                        if (isMoveMode) {
                            stopMoveMode(rv)
                        } else if (pos in currentBatch.indices) {
                            onAppClicked.invoke(currentBatch[pos])
                        }
                        return@setOnKeyListener true
                    }
                }

                // 2D Spatial Move Mode (reordering across batch boundaries)
                if (isMoveMode && globalPos == movingGlobalPosition && event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (globalPos > 0) {
                                moveItem(globalPos, globalPos - 1, rv)
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (globalPos < allApps.size - 1) {
                                moveItem(globalPos, globalPos + 1, rv)
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            val target = globalPos - cols
                            if (target >= 0) {
                                moveItem(globalPos, target, rv)
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val target = globalPos + cols
                            if (target < allApps.size) {
                                moveItem(globalPos, target, rv)
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            stopMoveMode(rv)
                            return@setOnKeyListener true
                        }
                    }
                    return@setOnKeyListener true
                }

                // Normal Mode 2D Spatial Discrete Page Navigation (Zero Edge Peeking)
                if (!isMoveMode && event.action == KeyEvent.ACTION_DOWN) {
                    val currentRow = pos / cols
                    val currentCol = pos % cols
                    val totalRowsInPage = if (currentBatch.isEmpty()) 0 else ((currentBatch.size - 1) / cols) + 1

                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (currentRow > 0) {
                                val targetLocal = pos - cols
                                rv?.findViewHolderForAdapterPosition(targetLocal)?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                                    ?: rv?.findViewHolderForAdapterPosition(targetLocal)?.itemView?.requestFocus()
                                return@setOnKeyListener true
                            } else if (currentPage > 0) {
                                // Flip back to previous page, landing on the bottom row (same column)
                                val prevStart = (currentPage - 1) * pageSize
                                val prevBatchSize = (allApps.size - prevStart).coerceAtMost(pageSize)
                                val targetLocal = ((2 * cols) + currentCol).coerceIn(0, prevBatchSize - 1)
                                setPage(currentPage - 1, targetLocal, rv, PageTransitionDirection.UP)
                                return@setOnKeyListener true
                            }
                            // If currentPage == 0 and currentRow == 0: bubbles up to MainActivity to return to Favorites
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (currentRow < totalRowsInPage - 1) {
                                val targetLocal = (pos + cols).coerceAtMost(currentBatch.size - 1)
                                rv?.findViewHolderForAdapterPosition(targetLocal)?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                                    ?: rv?.findViewHolderForAdapterPosition(targetLocal)?.itemView?.requestFocus()
                                return@setOnKeyListener true
                            } else if (currentPage < totalPages - 1) {
                                // Flip to next page, landing on the top row (same column)
                                val nextStart = (currentPage + 1) * pageSize
                                val nextBatchSize = (allApps.size - nextStart).coerceAtMost(pageSize)
                                val targetLocal = currentCol.coerceIn(0, nextBatchSize - 1)
                                setPage(currentPage + 1, targetLocal, rv, PageTransitionDirection.DOWN)
                                return@setOnKeyListener true
                            } else {
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (pos > 0) {
                                val targetLocal = pos - 1
                                rv?.findViewHolderForAdapterPosition(targetLocal)?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                                    ?: rv?.findViewHolderForAdapterPosition(targetLocal)?.itemView?.requestFocus()
                                return@setOnKeyListener true
                            } else if (currentPage > 0) {
                                val prevStart = (currentPage - 1) * pageSize
                                val prevBatchSize = (allApps.size - prevStart).coerceAtMost(pageSize)
                                setPage(currentPage - 1, prevBatchSize - 1, rv, PageTransitionDirection.LEFT)
                                return@setOnKeyListener true
                            }
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (pos < currentBatch.size - 1) {
                                val targetLocal = pos + 1
                                rv?.findViewHolderForAdapterPosition(targetLocal)?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                                    ?: rv?.findViewHolderForAdapterPosition(targetLocal)?.itemView?.requestFocus()
                                return@setOnKeyListener true
                            } else if (currentPage < totalPages - 1) {
                                setPage(currentPage + 1, 0, rv, PageTransitionDirection.RIGHT)
                                return@setOnKeyListener true
                            }
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }
        }

        fun bind(app: AppInfo, localPosition: Int, globalPosition: Int) {
            tvLabel.text = app.label

            if (app.bannerDrawable != null) {
                ivBanner.visibility = View.VISIBLE
                layoutFallback.visibility = View.GONE
                ivBanner.setImageDrawable(app.bannerDrawable)
            } else {
                ivBanner.visibility = View.GONE
                layoutFallback.visibility = View.VISIBLE
                ivIcon.setImageDrawable(app.iconDrawable)
            }

            val dark = isDarkMode.invoke()
            if (isMoveMode && globalPosition == movingGlobalPosition) {
                ivMoveBadge.visibility = View.VISIBLE
                cardContainer.setCardBackgroundColor(COLOR_MOVE_ACTIVE)
                tvLabel.setTextColor(COLOR_WHITE)
            } else {
                ivMoveBadge.visibility = View.GONE
                if (dark) {
                    cardContainer.setCardBackgroundColor(COLOR_DARK_CARD)
                    tvLabel.setTextColor(COLOR_DARK_CARD_TEXT)
                    tvLabel.setShadowLayer(4f, 0f, 1f, COLOR_DARK_SHADOW)
                } else {
                    cardContainer.setCardBackgroundColor(COLOR_LIGHT_CARD)
                    tvLabel.setTextColor(COLOR_LIGHT_CARD_TEXT)
                    tvLabel.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                }
            }

            // Unify focus outline & glow with Favorite Apps dock
            viewFocusGlow.setBackgroundResource(
                if (dark) R.drawable.bg_card_favorite_focus_dark else R.drawable.bg_card_favorite_focus_light
            )

            if (pendingFocusPosition != null && localPosition == pendingFocusPosition) {
                pendingFocusPosition = null
                cardContainer.post {
                    cardContainer.requestFocus()
                }
            }
        }
    }
}
