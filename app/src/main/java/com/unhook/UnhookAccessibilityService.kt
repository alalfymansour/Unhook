package com.unhook

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unhook.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Accessibility service for surgical swipe blocking in short-form video feeds.
 */
class UnhookAccessibilityService : AccessibilityService(), BlockSwipeOverlay.GesturePassthroughDispatcher {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tempRect = Rect()

    private lateinit var windowManager: WindowManager

    @Volatile
    private var currentForegroundPackage: String? = null

    @Volatile
    private var enabledPackages: Set<String> = AppPreferences.defaultTargetPackages

    @Volatile
    private var unhookEnabled: Boolean = true

    @Volatile
    private var isInVideoFeed: Boolean = false

    @Volatile
    private var lastContentScanUptime: Long = 0L

    @Volatile
    private var suppressOverlayUntilUptime: Long = 0L

    @Volatile
    private var allowReelsEntryUntilUptime: Long = 0L

    @Volatile
    private var tapBlockUntilUptime: Long = 0L

    private var overlayView: BlockSwipeOverlay? = null
    private var frictionDialogView: View? = null
    private var isNotificationVisible: Boolean = false
    private var debugNodeCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        ensurePersistentNotificationVisible()

        serviceScope.launch {
            AppPreferences.enabledPackagesFlow(this@UnhookAccessibilityService).collectLatest { packages ->
                enabledPackages = packages
                refreshBlockingState()
            }
        }

        serviceScope.launch {
            AppPreferences.unhookEnabledFlow(this@UnhookAccessibilityService).collectLatest { enabled ->
                unhookEnabled = enabled
                refreshBlockingState()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val safeEvent = event ?: return
        when (safeEvent.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(safeEvent)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowContentChanged(safeEvent)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleViewClicked(safeEvent)
        }
    }

    override fun onInterrupt() {
        // No-op. The service is event-driven.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        dismissFrictionDialog()
        removeOverlay()
        cancelPersistentNotification()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        dismissFrictionDialog()
        removeOverlay()
        cancelPersistentNotification()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun dispatchPassthroughGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
    ) {
        if (!shouldManageCurrentPackage()) {
            return
        }

        val safeDuration = durationMs.coerceIn(32L, 500L)
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, safeDuration))
            .build()

        suppressOverlayUntilUptime = SystemClock.uptimeMillis() + safeDuration + PASSTHROUGH_GAP_MS
        removeOverlay()

        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    applyOverlayBlockingMode()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    applyOverlayBlockingMode()
                }
            },
            null,
        )
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        debugNodeCount = 0

        val packageName = event.packageName?.toString() ?: return
        if (packageName != currentForegroundPackage) {
            currentForegroundPackage = packageName
            isInVideoFeed = false
            dismissFrictionDialog()
        }

        if (!shouldManagePackage(packageName)) {
            clearBlockingState()
            return
        }

        updateVideoFeedState(force = true)
        applyOverlayBlockingMode()
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: currentForegroundPackage ?: return
        if (packageName != currentForegroundPackage) {
            return
        }

        if (!shouldManagePackage(packageName)) {
            clearBlockingState()
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastContentScanUptime >= CONTENT_SCAN_DEBOUNCE_MS) {
            lastContentScanUptime = now
            updateVideoFeedState(force = false)
        }
        applyOverlayBlockingMode()
    }

    private fun handleViewClicked(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: currentForegroundPackage ?: return
        if (packageName != currentForegroundPackage || !shouldManagePackage(packageName)) {
            return
        }

        checkIfReelsEntryTapped(event, packageName)
    }

    private fun refreshBlockingState() {
        if (!shouldManageCurrentPackage()) {
            clearBlockingState()
            return
        }

        updateVideoFeedState(force = true)
        applyOverlayBlockingMode()
    }

    private fun applyOverlayBlockingMode() {
        ensurePersistentNotificationVisible()

        if (SystemClock.uptimeMillis() < suppressOverlayUntilUptime) {
            removeOverlay()
            return
        }

        if (!shouldManageCurrentPackage()) {
            removeOverlay()
            dismissFrictionDialog()
            return
        }

        val now = SystemClock.uptimeMillis()
        val shouldBlockVerticalSwipe = isInVideoFeed
        val shouldBlockTap = now < tapBlockUntilUptime

        if (!shouldBlockVerticalSwipe && !shouldBlockTap) {
            removeOverlay()
            return
        }

        addOverlayIfMissing()
        overlayView?.setBlockingMode(shouldBlockVerticalSwipe, shouldBlockTap)
    }

    private fun addOverlayIfMissing() {
        if (overlayView != null) {
            return
        }

        val overlay = BlockSwipeOverlay(this)
        try {
            windowManager.addView(overlay, buildOverlayLayoutParams())
            overlayView = overlay
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Failed to add swipe-block overlay", exception)
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        try {
            windowManager.removeViewImmediate(view)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Overlay removal failed", exception)
        } finally {
            overlayView = null
        }
    }

    private fun clearBlockingState() {
        isInVideoFeed = false
        tapBlockUntilUptime = 0L
        removeOverlay()
        dismissFrictionDialog()
    }

    private fun shouldManageCurrentPackage(): Boolean {
        val current = currentForegroundPackage ?: return false
        return shouldManagePackage(current)
    }

    private fun shouldManagePackage(packageName: String): Boolean {
        return unhookEnabled && enabledPackages.contains(packageName)
    }

    @Suppress("DEPRECATION")
    private fun updateVideoFeedState(force: Boolean) {
        val packageName = currentForegroundPackage ?: return
        if (!shouldManagePackage(packageName)) {
            isInVideoFeed = false
            return
        }
        if (packageName == TIKTOK_PACKAGE) {
            isInVideoFeed = true
            return
        }
        val root = rootInActiveWindow ?: run {
            Log.w("UnhookDebug", "[$packageName] rootInActiveWindow is NULL")
            if (force) isInVideoFeed = false
            return
        }
        try {
            Log.d("UnhookDebug", "[$packageName] root childCount=${root.childCount} | scanning tree...")
            val result = detectVideoFeedNode(root, packageName)
            Log.d("UnhookDebug", "[$packageName] detectVideoFeedNode=$result | wasInFeed=$isInVideoFeed")
            isInVideoFeed = result
        } finally {
            root.recycle()
        }
    }

    private fun detectVideoFeedNode(root: AccessibilityNodeInfo, packageName: String): Boolean {
        return when (packageName) {
            INSTAGRAM_PACKAGE -> {
                containsResourceIdPattern(
                    root,
                    "clips_viewer_view_pager",
                    "clips_video_container",
                    "clips_media_component",
                    "clips_pause_and_mute_component",
                )
            }

            FACEBOOK_PACKAGE -> {
                containsContentDescPattern(
                    root,
                    "reel details",
                    "tap to show video controls",
                    "fbshortscomposer",
                    "fb_shorts",
                )
            }

            YOUTUBE_PACKAGE -> {
                containsResourceIdPattern(
                    root,
                    "shorts_player",
                    "reel_player_page",
                    "shorts_shelf",
                ) || containsClassPattern(root, "ShortsActivity", "ReelWatchFragment")
            }

            X_PACKAGE -> {
                containsResourceIdPattern(
                    root,
                    "video_player_view",
                    "video_mute_toggle",
                    "video_play_toggle",
                )
            }

            TIKTOK_PACKAGE -> true
            else -> false
        }
    }

    private fun containsResourceIdPattern(root: AccessibilityNodeInfo, vararg patterns: String): Boolean {
        val loweredPatterns = patterns.map { it.lowercase(Locale.US) }
        return treeContains(root) { node ->
            val resourceId = node.viewIdResourceName.orEmpty().lowercase(Locale.US)
            loweredPatterns.any { resourceId.contains(it) }
        }
    }

    private fun containsContentDescPattern(
        root: AccessibilityNodeInfo,
        vararg patterns: String,
    ): Boolean {
        val loweredPatterns = patterns.map { it.lowercase(Locale.US) }
        return treeContains(root) { node ->
            val desc = node.contentDescription?.toString().orEmpty().lowercase(Locale.US)
            loweredPatterns.any { desc.contains(it) }
        }
    }

    private fun containsClassPattern(root: AccessibilityNodeInfo, vararg patterns: String): Boolean {
        val loweredPatterns = patterns.map { it.lowercase(Locale.US) }
        return treeContains(root) { node ->
            val className = node.className?.toString().orEmpty().lowercase(Locale.US)
            loweredPatterns.any { className.contains(it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun treeContains(
        root: AccessibilityNodeInfo,
        matcher: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        val pkg = currentForegroundPackage ?: ""
        val shouldLog = debugNodeCount < 40 && pkg in listOf(
            INSTAGRAM_PACKAGE,
            FACEBOOK_PACKAGE,
            X_PACKAGE,
        )
        if (shouldLog) {
            val rid = root.viewIdResourceName
            val cls = root.className?.toString() ?: ""
            val desc = root.contentDescription?.toString() ?: ""
            val scrollable = root.isScrollable
            if (!rid.isNullOrEmpty() || scrollable) {
                Log.d(
                    "UnhookDebug",
                    "[tree:${pkg.substringAfterLast('.')}] id=${rid ?: "null"} | class=$cls | scrollable=$scrollable | desc=${desc.take(40)}",
                )
                debugNodeCount++
            }
        }

        if (matcher(root)) return true
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            try {
                if (treeContains(child, matcher)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun checkIfReelsEntryTapped(event: AccessibilityEvent, packageName: String) {
        if (SystemClock.uptimeMillis() < allowReelsEntryUntilUptime) return
        val source = event.source ?: return
        val resourceId = source.viewIdResourceName.orEmpty().lowercase(Locale.US)
        val contentDesc = source.contentDescription?.toString().orEmpty().lowercase(Locale.US)
        source.recycle()

        val isEntryTap = when (packageName) {
            INSTAGRAM_PACKAGE -> {
                resourceId.contains("clips_tab") ||
                    contentDesc == "reels"
            }
            YOUTUBE_PACKAGE -> {
                (resourceId.contains("pivot_bar_item") && contentDesc.contains("shorts")) ||
                    contentDesc.contains("shorts")
            }
            FACEBOOK_PACKAGE -> {
                contentDesc.contains("reels") && contentDesc.contains("tab")
            }
            X_PACKAGE -> false
            else -> false
        }

        if (!isEntryTap) return

        tapBlockUntilUptime = SystemClock.uptimeMillis() + ENTRY_TAP_BLOCK_WINDOW_MS
        applyOverlayBlockingMode()
        performGlobalAction(GLOBAL_ACTION_BACK)
        showFrictionDialog(packageName)
    }

    private fun showFrictionDialog(packageName: String) {
        if (frictionDialogView != null) {
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xAA000000.toInt())
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121826"))
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }

        val titleText = TextView(this).apply {
            text = "You are about to enter the short-video loop"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        }

        val subtitle = TextView(this).apply {
            text = "Take a breath. Continue becomes available in 3 seconds."
            setTextColor(Color.parseColor("#C8D3F5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(10), 0, dp(18))
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val backButton = Button(this).apply {
            text = "Go Back"
            setOnClickListener { dismissFrictionDialog() }
        }

        val continueButton = Button(this).apply {
            text = "Continue"
            isEnabled = false
            alpha = 0.5f
            setOnClickListener {
                allowReelsEntryUntilUptime = SystemClock.uptimeMillis() + ENTRY_ALLOW_WINDOW_MS
                dismissFrictionDialog()
            }
        }

        buttons.addView(backButton)
        buttons.addView(
            continueButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(12)
            },
        )

        card.addView(titleText)
        card.addView(subtitle)
        card.addView(buttons)

        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                marginStart = dp(20)
                marginEnd = dp(20)
            },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            this.title = "UnhookFrictionDialog:$packageName"
        }

        try {
            windowManager.addView(root, params)
            frictionDialogView = root
            mainHandler.postDelayed({
                if (frictionDialogView != null) {
                    continueButton.isEnabled = true
                    continueButton.alpha = 1f
                }
            }, CONTINUE_DELAY_MS)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Could not show friction dialog", exception)
        }
    }

    private fun dismissFrictionDialog() {
        val dialog = frictionDialogView ?: return
        try {
            windowManager.removeViewImmediate(dialog)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Could not remove friction dialog", exception)
        } finally {
            frictionDialogView = null
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }

    private fun buildOverlayLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun ensurePersistentNotificationVisible() {
        if (isNotificationVisible || !canPostNotifications()) {
            return
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_unhook_notification)
            .setLargeIcon(notificationLogoBitmap)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        isNotificationVisible = true
    }

    private fun cancelPersistentNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        isNotificationVisible = false
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        private const val TAG = "UnhookService"
        private const val NOTIFICATION_CHANNEL_ID = "unhook_service"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_LOGO_WIDTH = 192
        private const val NOTIFICATION_LOGO_HEIGHT = 128

        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val FACEBOOK_PACKAGE = "com.facebook.katana"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val X_PACKAGE = "com.twitter.android"
        private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"

        private const val PASSTHROUGH_GAP_MS = 120L
        private const val ENTRY_TAP_BLOCK_WINDOW_MS = 1100L
        private const val ENTRY_ALLOW_WINDOW_MS = 7000L
        private const val CONTINUE_DELAY_MS = 3000L
        private const val CONTENT_SCAN_DEBOUNCE_MS = 500L
    }

    private val notificationLogoBitmap: Bitmap by lazy(LazyThreadSafetyMode.NONE) {
        val source = BitmapFactory.decodeResource(resources, R.drawable.unhook_logo)
        Bitmap.createScaledBitmap(
            source,
            NOTIFICATION_LOGO_WIDTH,
            NOTIFICATION_LOGO_HEIGHT,
            true,
        ).also { scaled ->
            if (scaled != source) {
                source.recycle()
            }
        }
    }
}
