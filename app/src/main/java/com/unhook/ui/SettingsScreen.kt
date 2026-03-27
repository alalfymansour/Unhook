package com.unhook.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unhook.R
import com.unhook.data.TargetApp

@Composable
fun SettingsScreen(
    appEnabled: Boolean,
    accessibilityEnabled: Boolean,
    enabledPackages: Set<String>,
    targetApps: List<TargetApp>,
    onAppEnabledChange: (Boolean) -> Unit,
    onPackageEnabledChange: (String, Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    showFirstLaunchPrompt: Boolean,
    onDismissPrompt: () -> Unit,
    onConfirmPrompt: () -> Unit,
) {
    val isActive = appEnabled && accessibilityEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.unhook_logo),
            contentDescription = "Unhook logo",
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = "Break infinite-scroll loops by intercepting vertical swipes only in selected apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFF132D5C) else Color(0xFF1A2338),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isActive) "Status: Active" else "Status: Inactive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (accessibilityEnabled) {
                        "Accessibility service is enabled."
                    } else {
                        "Accessibility service is disabled. Turn it on so Unhook can work."
                    },
                )
            }
        }

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
                        Column {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
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
