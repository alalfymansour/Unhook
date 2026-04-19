package com.unhook.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "unhook_prefs"
private val Context.dataStore by preferencesDataStore(name = STORE_NAME)

data class TargetApp(
    val label: String,
    val packageName: String,
)

/**
 * DataStore-backed preferences for enabled package targets and app state.
 */
object AppPreferences {
    private val enabledPackagesKey = stringSetPreferencesKey("enabled_packages")
    private val unhookEnabledKey = booleanPreferencesKey("unhook_enabled")
    private val hasSeenAccessibilityPromptKey = booleanPreferencesKey("has_seen_accessibility_prompt")

    val supportedApps: List<TargetApp> = listOf(
        TargetApp(label = "Instagram", packageName = SupportedPackages.INSTAGRAM),
        TargetApp(label = "Facebook", packageName = SupportedPackages.FACEBOOK),
        TargetApp(label = "YouTube", packageName = SupportedPackages.YOUTUBE),
        TargetApp(label = "X", packageName = SupportedPackages.X_APP),
        TargetApp(label = "TikTok", packageName = SupportedPackages.TIKTOK),
    )

    val defaultTargetPackages: Set<String> = supportedApps.map { it.packageName }.toSet()

    fun enabledPackagesFlow(context: Context): Flow<Set<String>> {
        return context.dataStore.data.map { prefs ->
            prefs[enabledPackagesKey] ?: defaultTargetPackages
        }
    }

    fun unhookEnabledFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[unhookEnabledKey] ?: true
        }
    }

    fun hasSeenAccessibilityPromptFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[hasSeenAccessibilityPromptKey] ?: false
        }
    }

    suspend fun setUnhookEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[unhookEnabledKey] = enabled
        }
    }

    suspend fun setPackageEnabled(context: Context, packageName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[enabledPackagesKey] ?: defaultTargetPackages).toMutableSet()
            if (enabled) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            prefs[enabledPackagesKey] = current
        }
    }

    suspend fun markAccessibilityPromptShown(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[hasSeenAccessibilityPromptKey] = true
        }
    }
}
