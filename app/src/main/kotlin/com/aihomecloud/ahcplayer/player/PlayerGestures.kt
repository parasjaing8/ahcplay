package com.aihomecloud.ahcplayer.player

import android.annotation.SuppressLint
import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

/**
 * Touch gestures matching what every mainstream mobile player offers: vertical swipe
 * on the left half for brightness and the right half for volume, horizontal swipe to
 * seek, double-tap either side to jump, and pinch to toggle fit/fill.
 *
 * D-pad devices never produce these events, so this layer is additive — it does not
 * change TV behaviour.
 */
class PlayerGestures(
    private val activity: Activity,
    private val callbacks: Callbacks
) : View.OnTouchListener {

    interface Callbacks {
        fun onSingleTap()
        fun onSeekBy(deltaMs: Long)
        fun onSeekScrubbing(deltaMs: Long)
        fun onVolumeChanged(fraction: Float)
        fun onBrightnessChanged(fraction: Float)
        fun onPinchToggle()
        fun isGestureAllowed(): Boolean
    }

    private companion object {
        const val DOUBLE_TAP_SEEK_MS = 10_000L
        /** Full-height swipe = full range; keeps the control feeling proportional. */
        const val SWIPE_RANGE_DIVISOR = 1.0f
        const val HORIZONTAL_SEEK_MS_PER_FRACTION = 90_000f
        const val SWIPE_SLOP_PX = 24f
    }

    private enum class Drag { NONE, VOLUME, BRIGHTNESS, SEEK }

    private var drag = Drag.NONE
    private var anchorX = 0f
    private var anchorY = 0f
    private var pendingSeekMs = 0L
    private var scaling = false

    private val gestureDetector = GestureDetector(activity, object :
        GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            callbacks.onSingleTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!callbacks.isGestureAllowed()) return false
            val forward = e.x > activity.window.decorView.width / 2f
            callbacks.onSeekBy(if (forward) DOUBLE_TAP_SEEK_MS else -DOUBLE_TAP_SEEK_MS)
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(activity, object :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scaling = true
            return callbacks.isGestureAllowed()
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            // Two discrete states rather than free zoom, which is what production
            // players do — continuous zoom on a video surface reads as jitter.
            if (abs(detector.scaleFactor - 1f) > 0.05f) callbacks.onPinchToggle()
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (gestureDetector.onTouchEvent(event)) return true
        if (scaling) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                scaling = false
                drag = Drag.NONE
            }
            return true
        }
        if (!callbacks.isGestureAllowed()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anchorX = event.x
                anchorY = event.y
                drag = Drag.NONE
                pendingSeekMs = 0L
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) return true
                val dx = event.x - anchorX
                val dy = event.y - anchorY

                if (drag == Drag.NONE) {
                    if (abs(dx) < SWIPE_SLOP_PX && abs(dy) < SWIPE_SLOP_PX) return true
                    drag = when {
                        abs(dx) > abs(dy) -> Drag.SEEK
                        anchorX < view.width / 2f -> Drag.BRIGHTNESS
                        else -> Drag.VOLUME
                    }
                    // Re-anchor so the control doesn't jump by the slop distance.
                    anchorX = event.x
                    anchorY = event.y
                    return true
                }

                when (drag) {
                    Drag.SEEK -> {
                        pendingSeekMs =
                            ((event.x - anchorX) / view.width * HORIZONTAL_SEEK_MS_PER_FRACTION).toLong()
                        callbacks.onSeekScrubbing(pendingSeekMs)
                    }
                    Drag.VOLUME -> {
                        val delta = -(event.y - anchorY) / (view.height * SWIPE_RANGE_DIVISOR)
                        anchorY = event.y
                        callbacks.onVolumeChanged(delta)
                    }
                    Drag.BRIGHTNESS -> {
                        val delta = -(event.y - anchorY) / (view.height * SWIPE_RANGE_DIVISOR)
                        anchorY = event.y
                        callbacks.onBrightnessChanged(delta)
                    }
                    Drag.NONE -> Unit
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (drag == Drag.SEEK && pendingSeekMs != 0L) {
                    callbacks.onSeekBy(pendingSeekMs)
                }
                drag = Drag.NONE
                pendingSeekMs = 0L
            }
        }
        return true
    }

    /** Window-local brightness; -1f hands control back to the system setting. */
    fun adjustBrightness(deltaFraction: Float): Float {
        val params = activity.window.attributes
        val current = if (params.screenBrightness < 0f) 0.5f else params.screenBrightness
        val target = (current + deltaFraction).coerceIn(0.01f, 1f)
        params.screenBrightness = target
        activity.window.attributes = params
        return target
    }
}
