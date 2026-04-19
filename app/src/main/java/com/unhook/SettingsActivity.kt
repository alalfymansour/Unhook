package com.unhook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import com.unhook.data.AppPreferences
import com.unhook.ui.SettingsScreen
import com.unhook.ui.theme.UnhookTheme
import kotlinx.coroutines.launch

open class SettingsActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            val lifecycleState by lifecycle.currentStateAsState()
            val scope = rememberCoroutineScope()

            val appEnabled by AppPreferences.unhookEnabledFlow(this).collectAsStateWithLifecycle(initialValue = true)
            val enabledPackages by AppPreferences.enabledPackagesFlow(this)
                .collectAsStateWithLifecycle(initialValue = AppPreferences.defaultTargetPackages)
            val hasSeenAccessibilityPrompt by AppPreferences.hasSeenAccessibilityPromptFlow(this)
                .collectAsStateWithLifecycle(initialValue = false)

            var accessibilityEnabled by remember { mutableStateOf(isServiceEnabled(this)) }
            var showFirstLaunchPrompt by remember { mutableStateOf(false) }

            LaunchedEffect(lifecycleState) {
                if (lifecycleState == Lifecycle.State.RESUMED) {
                    accessibilityEnabled = isServiceEnabled(this@SettingsActivity)
                }
            }

            LaunchedEffect(accessibilityEnabled, hasSeenAccessibilityPrompt) {
                showFirstLaunchPrompt = !accessibilityEnabled && !hasSeenAccessibilityPrompt
            }

            UnhookTheme {
                Surface {
                    SettingsScreen(
                        appEnabled = appEnabled,
                        enabledPackages = enabledPackages,
                        targetApps = AppPreferences.supportedApps,
                        onAppEnabledChange = { enabled ->
                            scope.launch {
                                AppPreferences.setUnhookEnabled(this@SettingsActivity, enabled)
                            }
                        },
                        onPackageEnabledChange = { packageName, enabled ->
                            scope.launch {
                                AppPreferences.setPackageEnabled(this@SettingsActivity, packageName, enabled)
                            }
                        },
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        showFirstLaunchPrompt = showFirstLaunchPrompt,
                        onDismissPrompt = {
                            scope.launch {
                                AppPreferences.markAccessibilityPromptShown(this@SettingsActivity)
                            }
                            showFirstLaunchPrompt = false
                        },
                        onConfirmPrompt = {
                            scope.launch {
                                AppPreferences.markAccessibilityPromptShown(this@SettingsActivity)
                            }
                            showFirstLaunchPrompt = false
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                }
            }
        }
    }

    private fun isServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, UnhookAccessibilityService::class.java)
        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        return enabledSetting
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expectedComponent }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
