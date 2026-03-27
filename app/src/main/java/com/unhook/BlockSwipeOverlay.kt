package com.unhook

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Transparent full-screen overlay that can selectively block vertical swipes
 * and/or taps while replaying allowed gestures via accessibility dispatch.
 */
class BlockSwipeOverlay(context: Context) : View(context) {
    interface GesturePassthroughDispatcher {
        fun dispatchPassthroughGesture(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long,
        )
    }

    private val passthroughDispatcher = context as? GesturePassthroughDispatcher

    private var consumeCurrentGesture: Boolean = false
    private var movedDistancePx: Float = 0f
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var downTimeMs: Long = 0L

    var blockVerticalSwipe: Boolean = false
        private set

    var blockTap: Boolean = false
        private set

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (!blockVerticalSwipe) {
                    return false
                }

                val isVerticalSwipe =
                    abs(distanceY) > SWIPE_THRESHOLD &&
                        abs(distanceY) > abs(distanceX) * HORIZONTAL_RATIO_LIMIT

                if (isVerticalSwipe) {
                    consumeCurrentGesture = true
                }

                return isVerticalSwipe
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                return blockTap
            }
        },
    )

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setBlockingMode(blockVerticalSwipe: Boolean, blockTap: Boolean) {
        this.blockVerticalSwipe = blockVerticalSwipe
        this.blockTap = blockTap
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!blockVerticalSwipe && !blockTap) {
            return false
        }

        gestureDetector.onTouchEvent(event)

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                consumeCurrentGesture = false
                movedDistancePx = 0f
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                downTimeMs = SystemClock.uptimeMillis()
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                movedDistancePx += hypot(dx.toDouble(), dy.toDouble()).toFloat()
                lastX = event.x
                lastY = event.y
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val shouldConsume = consumeCurrentGesture
                val isTap = movedDistancePx <= TAP_SLOP_PX

                if (
                    !shouldConsume &&
                    !(blockTap && isTap) &&
                    event.actionMasked == MotionEvent.ACTION_UP
                ) {
                    passthroughDispatcher?.dispatchPassthroughGesture(
                        startX = downX,
                        startY = downY,
                        endX = lastX,
                        endY = lastY,
                        durationMs = (SystemClock.uptimeMillis() - downTimeMs),
                    )
                }

                consumeCurrentGesture = false
                movedDistancePx = 0f
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                }
                true
            }

            else -> true
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private companion object {
        private const val SWIPE_THRESHOLD = 40f
        private const val HORIZONTAL_RATIO_LIMIT = 1.5f
        private const val TAP_SLOP_PX = 18f
    }
}
