package com.andsmarttv.launcher.ui.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.SoundEffectConstants
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Handles smooth Android TV scale up and custom focus aura/glow effects.
 */
object FocusHighlightHelper {

    private const val FOCUSED_SCALE = 1.06f
    private const val UNFOCUSED_SCALE = 1.0f
    private const val DURATION_MS = 120L

    /**
     * Attaches smooth scale and focus handling to a View.
     */
    fun attachFocusAnimation(
        view: View,
        focusGlowView: View? = null,
        onFocusChanged: ((Boolean) -> Unit)? = null
    ) {
        view.setOnFocusChangeListener { v, hasFocus ->
            animateFocus(v, hasFocus, focusGlowView)
            if (hasFocus) {
                v.playSoundEffect(SoundEffectConstants.CLICK)
            }
            onFocusChanged?.invoke(hasFocus)
        }
    }

    private fun animateFocus(view: View, hasFocus: Boolean, focusGlowView: View? = null) {
        val targetScale = if (hasFocus) FOCUSED_SCALE else UNFOCUSED_SCALE
        
        // Keep hardware elevation and translationZ at 0 to avoid irregular drop-shadow artifacts
        view.elevation = 0f
        view.translationZ = 0f

        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, targetScale)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, targetScale)

        val animList = mutableListOf<android.animation.Animator>(scaleX, scaleY)

        focusGlowView?.let { glow ->
            if (hasFocus) {
                glow.visibility = View.VISIBLE
                glow.alpha = 0f
                val alphaAnim = ObjectAnimator.ofFloat(glow, View.ALPHA, 1.0f)
                animList.add(alphaAnim)
            } else {
                glow.visibility = View.GONE
            }
        }

        AnimatorSet().apply {
            playTogether(animList)
            duration = DURATION_MS
            interpolator = DecelerateInterpolator()
            start()
        }
    }
}
