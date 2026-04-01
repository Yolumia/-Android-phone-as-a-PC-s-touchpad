package com.motorola.motomouse.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.motorola.motomouse.data.TouchpadHapticSettings
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

@Composable
fun TouchpadScreen(
    hapticSettings: TouchpadHapticSettings,
    onPointerMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onScroll: (Int, Int) -> Unit,
    onZoom: (Int) -> Unit,
    onGesture: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val hapticController = rememberTouchpadHapticController()
    val gestureThresholds = remember(density) {
        GestureThresholds(
            tapSlopPx = with(density) { 14.dp.toPx() },
            movementActivationPx = with(density) { 10.dp.toPx() },
            dragActivationPx = with(density) { 18.dp.toPx() },
            scrollActivationPx = with(density) { 12.dp.toPx() },
            multiSwipeThresholdPx = with(density) { 64.dp.toPx() },
            zoomStepPx = with(density) { 24.dp.toPx() },
            zoomLockPx = with(density) { 18.dp.toPx() },
            inertiaStartVelocity = 170f,
        )
    }

    val currentPointerMove by rememberUpdatedState(onPointerMove)
    val currentLeftClick by rememberUpdatedState(onLeftClick)
    val currentRightClick by rememberUpdatedState(onRightClick)
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDragMove by rememberUpdatedState(onDragMove)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentScroll by rememberUpdatedState(onScroll)
    val currentZoom by rememberUpdatedState(onZoom)
    val currentGesture by rememberUpdatedState(onGesture)

    val gestureProcessor = remember(view, gestureThresholds) {
        TouchpadGestureProcessor(
            view = view,
            thresholds = gestureThresholds,
        )
    }.apply {
        this.onPointerMove = currentPointerMove
        this.onLeftClick = {
            hapticController.perform(TouchpadHapticType.PrimaryClick, hapticSettings)
            currentLeftClick()
        }
        this.onRightClick = {
            hapticController.perform(TouchpadHapticType.SecondaryClick, hapticSettings)
            currentRightClick()
        }
        this.onDragStart = {
            hapticController.perform(TouchpadHapticType.DragStart, hapticSettings)
            currentDragStart()
        }
        this.onDragMove = currentDragMove
        this.onDragEnd = currentDragEnd
        this.onScroll = currentScroll
        this.onZoom = currentZoom
        this.onGesture = currentGesture
    }

    DisposableEffect(gestureProcessor) {
        onDispose {
            gestureProcessor.dispose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInteropFilter { motionEvent ->
                gestureProcessor.onTouchEvent(motionEvent)
            },
    ) {

        Text(
            text = "触摸板",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)

                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
        )
    }
}

private data class GestureThresholds(
    val tapSlopPx: Float,
    val movementActivationPx: Float,
    val dragActivationPx: Float,
    val scrollActivationPx: Float,
    val multiSwipeThresholdPx: Float,
    val zoomStepPx: Float,
    val zoomLockPx: Float,
    val inertiaStartVelocity: Float,
)

private class TouchpadGestureProcessor(
    private val view: View,
    private val thresholds: GestureThresholds,
) {
    var onPointerMove: (Float, Float) -> Unit = { _, _ -> }
    var onLeftClick: () -> Unit = {}
    var onRightClick: () -> Unit = {}
    var onDragStart: () -> Unit = {}
    var onDragMove: (Float, Float) -> Unit = { _, _ -> }
    var onDragEnd: () -> Unit = {}
    var onScroll: (Int, Int) -> Unit = { _, _ -> }
    var onZoom: (Int) -> Unit = {}
    var onGesture: (String) -> Unit = {}

    private val lastPositions = mutableMapOf<Int, Offset>()
    private val scrollMomentum = ScrollMomentum(view) { dx, dy ->
        emitScroll(dx, dy)
    }

    private var lastTapUpTime = 0L
    private var lastEventTime = 0L
    private var secondTapGesture = false
    private var maxPointers = 0
    private var singlePointerTravel = 0f
    private var twoFingerTravel = 0f
    private var pinchTravel = 0f
    private var zoomAccumulator = 0f
    private var dragStarted = false
    private var pinchLocked = false
    private var scrollActive = false
    private var threeFingerDx = 0f
    private var fourFingerDx = 0f
    private var scrollVelocityX = 0f
    private var scrollVelocityY = 0f
    private var scrollRemainderX = 0f
    private var scrollRemainderY = 0f

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginGesture(event)
            MotionEvent.ACTION_POINTER_DOWN -> {
                scrollMomentum.stop()
                maxPointers = max(maxPointers, event.pointerCount)
                updateLastPositions(event)
                lastEventTime = event.eventTime
            }

            MotionEvent.ACTION_MOVE -> {
                handleMove(event)
                updateLastPositions(event)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                updateLastPositionsExcludingActionIndex(event)
                lastEventTime = event.eventTime
            }

            MotionEvent.ACTION_UP -> finishGesture(cancelled = false, eventTime = event.eventTime)
            MotionEvent.ACTION_CANCEL -> finishGesture(cancelled = true, eventTime = event.eventTime)
        }
        return true
    }

    fun dispose() {
        scrollMomentum.dispose()
    }

    private fun beginGesture(event: MotionEvent) {
        scrollMomentum.stop()
        resetTransientState()
        secondTapGesture = SystemClock.uptimeMillis() - lastTapUpTime <= DOUBLE_TAP_WINDOW_MS
        maxPointers = event.pointerCount
        lastEventTime = event.eventTime
        updateLastPositions(event)
    }

    private fun handleMove(event: MotionEvent) {
        if (event.pointerCount == 0) return
        val dtMs = (event.eventTime - lastEventTime).coerceAtLeast(1L)
        maxPointers = max(maxPointers, event.pointerCount)

        when (event.pointerCount) {
            1 -> if (maxPointers == 1) processSinglePointerMove(event)
            2 -> if (maxPointers <= 2) processTwoFingerMove(event, dtMs)
            3 -> processThreeFingerMove(event)
            else -> processFourFingerMove(event)
        }

        lastEventTime = event.eventTime
    }

    private fun processSinglePointerMove(event: MotionEvent) {
        val pointerId = event.getPointerId(0)
        val previous = lastPositions[pointerId] ?: return
        val current = Offset(event.getX(0), event.getY(0))
        val delta = current - previous
        singlePointerTravel += delta.getDistance()

        if (secondTapGesture && !dragStarted && singlePointerTravel > thresholds.dragActivationPx) {
            dragStarted = true
            onDragStart()
        }

        if (dragStarted) {
            onDragMove(delta.x, delta.y)
        } else if (singlePointerTravel > thresholds.movementActivationPx) {
            onPointerMove(delta.x, delta.y)
        }
    }

    private fun processTwoFingerMove(event: MotionEvent, dtMs: Long) {
        val firstId = event.getPointerId(0)
        val secondId = event.getPointerId(1)
        val previousFirst = lastPositions[firstId] ?: return
        val previousSecond = lastPositions[secondId] ?: return
        val currentFirst = Offset(event.getX(0), event.getY(0))
        val currentSecond = Offset(event.getX(1), event.getY(1))
        val currentCentroid = (currentFirst + currentSecond) / 2f
        val previousCentroid = (previousFirst + previousSecond) / 2f
        val centroidDelta = currentCentroid - previousCentroid

        twoFingerTravel += centroidDelta.getDistance()
        val pinchDelta = (currentFirst - currentSecond).getDistance() - (previousFirst - previousSecond).getDistance()
        pinchTravel += pinchDelta

        if (!pinchLocked && abs(pinchTravel) > thresholds.zoomLockPx && abs(pinchTravel) > twoFingerTravel * 0.6f) {
            pinchLocked = true
        }

        if (pinchLocked) {
            zoomAccumulator += pinchDelta / thresholds.zoomStepPx
            while (abs(zoomAccumulator) >= 1f) {
                val step = if (zoomAccumulator > 0f) 1 else -1
                onZoom(step)
                zoomAccumulator -= step.toFloat()
            }
            return
        }

        val scrollDx = macScrollDelta(centroidDelta.x)
        val scrollDy = macScrollDelta(-centroidDelta.y)
        if (twoFingerTravel > thresholds.scrollActivationPx || abs(scrollDx) > 0.35f || abs(scrollDy) > 0.35f) {
            scrollActive = true
        }
        emitScroll(scrollDx, scrollDy)

        val instantVelocityX = scrollDx / dtMs * 1000f
        val instantVelocityY = scrollDy / dtMs * 1000f
        scrollVelocityX = blend(scrollVelocityX, instantVelocityX, 0.34f)
        scrollVelocityY = blend(scrollVelocityY, instantVelocityY, 0.34f)
    }

    private fun processThreeFingerMove(event: MotionEvent) {
        val current = centroidOf(event, 3)
        val previous = previousCentroidOf(event, 3) ?: return
        threeFingerDx += (current - previous).x
    }

    private fun processFourFingerMove(event: MotionEvent) {
        val pointerCount = minOf(event.pointerCount, 4)
        val current = centroidOf(event, pointerCount)
        val previous = previousCentroidOf(event, pointerCount) ?: return
        fourFingerDx += (current - previous).x
    }

    private fun finishGesture(cancelled: Boolean, eventTime: Long) {
        val shouldStartMomentum = !cancelled && maxPointers == 2 && scrollActive && !pinchLocked
        val velocityX = scrollVelocityX
        val velocityY = scrollVelocityY

        if (cancelled) {
            if (dragStarted) {
                onDragEnd()
            }
            lastTapUpTime = 0L
            scrollMomentum.stop()
        } else {
            when {
                dragStarted -> {
                    onDragEnd()
                    lastTapUpTime = 0L
                }

                maxPointers == 1 && singlePointerTravel <= thresholds.tapSlopPx -> {
                    onLeftClick()
                    lastTapUpTime = SystemClock.uptimeMillis()
                }

                maxPointers == 2 && !pinchLocked && !scrollActive && twoFingerTravel <= thresholds.tapSlopPx * 1.5f && abs(pinchTravel) <= thresholds.tapSlopPx -> {
                    onRightClick()
                    lastTapUpTime = 0L
                }

                maxPointers == 3 && abs(threeFingerDx) > thresholds.multiSwipeThresholdPx -> {
                    onGesture(if (threeFingerDx < 0f) APP_SWITCH_NEXT else APP_SWITCH_PREVIOUS)
                    lastTapUpTime = 0L
                }

                maxPointers >= 4 && abs(fourFingerDx) > thresholds.multiSwipeThresholdPx -> {
                    onGesture(if (fourFingerDx < 0f) DESKTOP_NEXT else DESKTOP_PREVIOUS)
                    lastTapUpTime = 0L
                }

                else -> {
                    lastTapUpTime = 0L
                }
            }

            if (shouldStartMomentum && hypot(velocityX.toDouble(), velocityY.toDouble()) >= thresholds.inertiaStartVelocity.toDouble()) {
                scrollMomentum.start(velocityX, velocityY)
            } else {
                scrollMomentum.stop()
            }
        }

        lastPositions.clear()
        lastEventTime = 0L
        resetTransientState()
    }

    private fun emitScroll(dx: Float, dy: Float) {
        scrollRemainderX += dx
        scrollRemainderY += dy
        val outX = scrollRemainderX.toInt()
        val outY = scrollRemainderY.toInt()
        if (outX != 0 || outY != 0) {
            onScroll(outX, outY)
            scrollRemainderX -= outX
            scrollRemainderY -= outY
        }
    }

    private fun updateLastPositions(event: MotionEvent) {
        lastPositions.clear()
        repeat(event.pointerCount) { index ->
            lastPositions[event.getPointerId(index)] = Offset(event.getX(index), event.getY(index))
        }
    }

    private fun updateLastPositionsExcludingActionIndex(event: MotionEvent) {
        lastPositions.clear()
        repeat(event.pointerCount) { index ->
            if (index != event.actionIndex) {
                lastPositions[event.getPointerId(index)] = Offset(event.getX(index), event.getY(index))
            }
        }
    }

    private fun centroidOf(event: MotionEvent, count: Int): Offset {
        var totalX = 0f
        var totalY = 0f
        repeat(count) { index ->
            totalX += event.getX(index)
            totalY += event.getY(index)
        }
        return Offset(totalX / count, totalY / count)
    }

    private fun previousCentroidOf(event: MotionEvent, count: Int): Offset? {
        var totalX = 0f
        var totalY = 0f
        repeat(count) { index ->
            val previous = lastPositions[event.getPointerId(index)] ?: return null
            totalX += previous.x
            totalY += previous.y
        }
        return Offset(totalX / count, totalY / count)
    }

    private fun resetTransientState() {
        secondTapGesture = false
        maxPointers = 0
        singlePointerTravel = 0f
        twoFingerTravel = 0f
        pinchTravel = 0f
        zoomAccumulator = 0f
        dragStarted = false
        pinchLocked = false
        scrollActive = false
        threeFingerDx = 0f
        fourFingerDx = 0f
        scrollVelocityX = 0f
        scrollVelocityY = 0f
        scrollRemainderX = 0f
        scrollRemainderY = 0f
    }
}

private class ScrollMomentum(
    private val view: View,
    private val onStep: (Float, Float) -> Unit,
) {
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastFrameTime = 0L

    private val frameRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dtMs = (now - lastFrameTime).coerceAtLeast(1L)
            lastFrameTime = now
            val seconds = dtMs / 1000f
            onStep(velocityX * seconds, velocityY * seconds)

            val decay = exp((-dtMs / FLING_TIME_CONSTANT_MS).toDouble()).toFloat()
            velocityX *= decay
            velocityY *= decay
            if (hypot(velocityX.toDouble(), velocityY.toDouble()) < MIN_FLING_VELOCITY.toDouble()) {
                stop()
                return
            }
            view.postOnAnimation(this)
        }
    }

    fun start(velocityX: Float, velocityY: Float) {
        this.velocityX = velocityX.coerceIn(-MAX_FLING_VELOCITY, MAX_FLING_VELOCITY)
        this.velocityY = velocityY.coerceIn(-MAX_FLING_VELOCITY, MAX_FLING_VELOCITY)
        lastFrameTime = SystemClock.uptimeMillis()
        view.removeCallbacks(frameRunnable)
        view.postOnAnimation(frameRunnable)
    }

    fun stop() {
        velocityX = 0f
        velocityY = 0f
        view.removeCallbacks(frameRunnable)
    }

    fun dispose() {
        stop()
    }
}

private fun macScrollDelta(deltaPx: Float): Float {
    val normalized = deltaPx / 4.8f
    val acceleration = 1f + (abs(deltaPx) / 42f).coerceAtMost(1.15f)
    return normalized * acceleration
}

private fun blend(previous: Float, current: Float, factor: Float): Float {
    return previous + (current - previous) * factor
}

private fun toPercent(value: Float): String {
    return "${(value.coerceIn(0f, 1f) * 100).toInt()}%"
}

private fun frequencyLabel(value: Float): String {
    return when {
        value < 0.33f -> "柔和"
        value < 0.66f -> "平衡"
        else -> "紧致"
    }
}


private const val DOUBLE_TAP_WINDOW_MS = 320L
private const val APP_SWITCH_NEXT = "app_switch_next"
private const val APP_SWITCH_PREVIOUS = "app_switch_previous"
private const val DESKTOP_NEXT = "desktop_next"
private const val DESKTOP_PREVIOUS = "desktop_previous"
private const val FLING_TIME_CONSTANT_MS = 220f
private const val MIN_FLING_VELOCITY = 18f
private const val MAX_FLING_VELOCITY = 2_200f
