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

class FavoritesAdapter(
    private val context: Context,
    private val isDarkMode: () -> Boolean,
    private val onAppClicked: (AppInfo) -> Unit,
    private val onAppOptionsRequested: (AppInfo, Int) -> Unit,
    private val onOrderChanged: (List<AppInfo>) -> Unit,
    var onMoveModeChanged: ((Boolean) -> Unit)? = null
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    companion object {
        private val COLOR_MOVE_ACTIVE = Color.parseColor("#3B82F6")
        private val COLOR_WHITE = Color.parseColor("#FFFFFF")
        private val COLOR_DARK_CARD = Color.parseColor("#1E293B")
        private val COLOR_DARK_CARD_TEXT = Color.parseColor("#FFFFFF")
        private val COLOR_DARK_SHADOW = Color.parseColor("#99000000")
        private val COLOR_LIGHT_CARD = Color.parseColor("#E2E8F0")
        private val COLOR_LIGHT_CARD_TEXT = Color.parseColor("#0F172A")
    }

    private val favoriteList = mutableListOf<AppInfo>()
    var isMoveMode = false
        private set
    private var movingPosition: Int = RecyclerView.NO_POSITION

    fun submitList(newList: List<AppInfo>) {
        favoriteList.clear()
        favoriteList.addAll(newList)
        notifyDataSetChanged()
    }

    fun startMoveMode(position: Int, rv: RecyclerView? = null) {
        if (position in 0 until favoriteList.size) {
            isMoveMode = true
            movingPosition = position
            onMoveModeChanged?.invoke(true)
            notifyItemChanged(position)
            rv?.post {
                val vh = rv.findViewHolderForAdapterPosition(position)
                vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                    ?: vh?.itemView?.requestFocus()
            }
        }
    }

    fun stopMoveMode(rv: RecyclerView? = null) {
        if (isMoveMode) {
            val oldPos = movingPosition
            isMoveMode = false
            movingPosition = RecyclerView.NO_POSITION
            if (oldPos != RecyclerView.NO_POSITION && oldPos in favoriteList.indices) {
                notifyItemChanged(oldPos)
            }
            onOrderChanged.invoke(favoriteList)

            if (oldPos != RecyclerView.NO_POSITION && oldPos in favoriteList.indices) {
                rv?.post {
                    val vh = rv.findViewHolderForAdapterPosition(oldPos)
                    vh?.itemView?.findViewById<View>(R.id.cardContainer)?.requestFocus()
                        ?: vh?.itemView?.requestFocus()
                    onMoveModeChanged?.invoke(false)
                }
            } else {
                onMoveModeChanged?.invoke(false)
            }
        }
    }

    fun moveItem(fromPos: Int, toPos: Int) {
        if (fromPos in 0 until favoriteList.size && toPos in 0 until favoriteList.size) {
            val item = favoriteList.removeAt(fromPos)
            favoriteList.add(toPos, item)
            movingPosition = toPos
            notifyItemMoved(fromPos, toPos)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_card, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(favoriteList[position], position)
    }

    override fun getItemCount(): Int = favoriteList.size

    inner class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
                if (pos == RecyclerView.NO_POSITION || pos !in favoriteList.indices) return@setOnTouchListener false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressTriggered = false
                        if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                            isLongPressTriggered = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onAppOptionsRequested.invoke(favoriteList[pos], pos)
                            return@setOnTouchListener true
                        }
                        longPressRunnable = Runnable {
                            val currentPos = bindingAdapterPosition
                            if (currentPos != RecyclerView.NO_POSITION && currentPos in favoriteList.indices) {
                                isLongPressTriggered = true
                                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onAppOptionsRequested.invoke(favoriteList[currentPos], currentPos)
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
                            } else if (pos in favoriteList.indices) {
                                onAppClicked.invoke(favoriteList[pos])
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

            // Remote D-Pad key handling
            cardContainer.setOnKeyListener { v, keyCode, event ->
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos !in favoriteList.indices) return@setOnKeyListener false
                val rv = (v.parent?.parent ?: v.parent) as? RecyclerView

                if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
                    isLongPressTriggered = true
                    onAppOptionsRequested.invoke(favoriteList[pos], pos)
                    return@setOnKeyListener true
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (event.repeatCount == 0) {
                            isLongPressTriggered = false
                            longPressRunnable = Runnable {
                                val currentPos = bindingAdapterPosition
                                if (currentPos != RecyclerView.NO_POSITION && currentPos in favoriteList.indices) {
                                    isLongPressTriggered = true
                                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onAppOptionsRequested.invoke(favoriteList[currentPos], currentPos)
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
                        } else if (pos in favoriteList.indices) {
                            onAppClicked.invoke(favoriteList[pos])
                        }
                        return@setOnKeyListener true
                    }
                }

                if (isMoveMode && pos == movingPosition && event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (pos > 0) {
                                moveItem(pos, pos - 1)
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (pos < favoriteList.size - 1) {
                                moveItem(pos, pos + 1)
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
                }
                false
            }
        }

        fun bind(app: AppInfo, position: Int) {
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
            if (isMoveMode && position == movingPosition) {
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

            viewFocusGlow.setBackgroundResource(
                if (dark) R.drawable.bg_card_favorite_focus_dark else R.drawable.bg_card_favorite_focus_light
            )
        }
    }
}
