package com.unhook.ui

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.unhook.R
import com.unhook.data.SupportedPackages
import com.unhook.data.TargetApp

@Composable
fun SettingsScreen(
    appEnabled: Boolean,
    enabledPackages: Set<String>,
    targetApps: List<TargetApp>,
    onAppEnabledChange: (Boolean) -> Unit,
    onPackageEnabledChange: (String, Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    showFirstLaunchPrompt: Boolean,
    onDismissPrompt: () -> Unit,
    onConfirmPrompt: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.unhook_logo),
            contentDescription = "Unhook logo",
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = "Unhook yourself from corporations' chains.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )

        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Unhook",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Master switch for gesture blocking.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = appEnabled, onCheckedChange = onAppEnabledChange)
            }
        }

        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Accessibility Settings")
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Target Apps",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                targetApps.forEachIndexed { index, app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = enabledPackages.contains(app.packageName),
                            onCheckedChange = { checked ->
                                onPackageEnabledChange(app.packageName, checked)
                            },
                        )
                        TargetAppIcon(packageName = app.packageName, label = app.label)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    if (index != targetApps.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }

        Text(
            text = "Unhook is fully offline. No analytics, no network calls, no tracking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )

        Button(
            onClick = {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_REPO_URL))
                context.startActivity(browserIntent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Official GitHub Repository")
        }
    }

    if (showFirstLaunchPrompt) {
        AlertDialog(
            onDismissRequest = onDismissPrompt,
            title = { Text("Enable Accessibility") },
            text = {
                Text(
                    "To block infinite vertical scrolling, enable Unhook in Android Accessibility settings.",
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissPrompt) {
                    Text("Later")
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmPrompt) {
                    Text("Open Settings")
                }
            },
        )
    }
}

private const val OFFICIAL_REPO_URL = "https://github.com/alalfymansour/Unhook.git"

@Composable
private fun TargetAppIcon(packageName: String, label: String) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        val packagesForIcon = when (packageName) {
            SupportedPackages.TIKTOK -> SupportedPackages.tiktokIconCandidates
            else -> listOf(packageName)
        }

        packagesForIcon.firstNotNullOfOrNull { candidatePackage ->
            runCatching {
                val appInfo = context.packageManager.getApplicationInfo(candidatePackage, 0)
                appInfo.loadUnbadgedIcon(context.packageManager)
            }.getOrNull()
        }
    }

    AndroidView(
        modifier = Modifier.size(24.dp),
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
        },
        update = { imageView ->
            imageView.contentDescription = "$label icon"
            imageView.setImageDrawable(
                appIcon ?: ContextCompat.getDrawable(context, R.drawable.ic_unhook_foreground),
            )
        },
    )
}
