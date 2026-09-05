package com.tuneitall.tuner.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tuneitall.tuner.R
import com.tuneitall.tuner.autoscroll.AutoScrollOverlayService
import com.tuneitall.tuner.autoscroll.AutoScrollPermissionState
import com.tuneitall.tuner.autoscroll.AutoScrollPreferences
import com.tuneitall.tuner.autoscroll.AutoScrollSpeed
import kotlin.math.roundToInt

@Composable
fun AutoScrollRoute() {
    val context = LocalContext.current
    val preferences = remember(context) { AutoScrollPreferences(context) }
    var overlayAllowed by remember { mutableStateOf(AutoScrollPermissionState.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(AutoScrollPermissionState.isAccessibilityEnabled(context)) }
    var speed by remember { mutableIntStateOf(preferences.speed) }
    var disclosureAccepted by remember { mutableStateOf(preferences.disclosureAccepted) }

    LifecycleResumeEffect(context) {
        overlayAllowed = AutoScrollPermissionState.canDrawOverlays(context)
        accessibilityEnabled = AutoScrollPermissionState.isAccessibilityEnabled(context)
        onPauseOrDispose { }
    }

    AutoScrollScreen(
        overlayAllowed = overlayAllowed,
        accessibilityEnabled = accessibilityEnabled,
        disclosureAccepted = disclosureAccepted,
        speed = speed,
        onSpeedChanged = {
            speed = AutoScrollSpeed.clamp(it)
            preferences.speed = speed
        },
        onOpenOverlaySettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        },
        onOpenAccessibilitySettings = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onDisclosureAccepted = {
            disclosureAccepted = true
            preferences.disclosureAccepted = true
        },
        onShowControls = {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AutoScrollOverlayService::class.java).setAction(AutoScrollOverlayService.ACTION_SHOW),
                )
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.auto_scroll_start_failed, Toast.LENGTH_LONG).show()
            } catch (_: IllegalStateException) {
                Toast.makeText(context, R.string.auto_scroll_start_failed, Toast.LENGTH_LONG).show()
            }
        },
    )
}

@Composable
fun AutoScrollScreen(
    overlayAllowed: Boolean,
    accessibilityEnabled: Boolean,
    disclosureAccepted: Boolean = false,
    speed: Int,
    onSpeedChanged: (Int) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onDisclosureAccepted: () -> Unit = {},
    onShowControls: () -> Unit,
) {
    var showDisclosure by remember { mutableStateOf(false) }
    val decreaseSpeedDescription = stringResource(R.string.auto_scroll_decrease_speed)
    val increaseSpeedDescription = stringResource(R.string.auto_scroll_increase_speed)
    val speedDescription = stringResource(R.string.auto_scroll_speed)
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.auto_scroll_disclosure_title)) },
            text = { Text(stringResource(R.string.auto_scroll_disclosure_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDisclosureAccepted()
                        showDisclosure = false
                        onOpenAccessibilitySettings()
                    },
                    modifier = Modifier.testTag("auto_scroll_disclosure_continue"),
                ) {
                    Text(stringResource(R.string.auto_scroll_disclosure_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisclosure = false },
                    modifier = Modifier.testTag("auto_scroll_disclosure_cancel"),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            modifier = Modifier.testTag("auto_scroll_disclosure"),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("auto_scroll_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.auto_scroll_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.auto_scroll_description), style = MaterialTheme.typography.bodyLarge)
        PermissionRow(
            title = stringResource(R.string.auto_scroll_overlay_permission),
            granted = overlayAllowed,
            action = stringResource(R.string.auto_scroll_allow_overlay),
            onClick = onOpenOverlaySettings,
            tag = "auto_scroll_overlay_permission",
        )
        PermissionRow(
            title = stringResource(R.string.auto_scroll_accessibility_permission),
            granted = accessibilityEnabled,
            action = stringResource(R.string.auto_scroll_enable_accessibility),
            onClick = {
                if (disclosureAccepted) onOpenAccessibilitySettings() else showDisclosure = true
            },
            tag = "auto_scroll_accessibility_permission",
            actionTag = "auto_scroll_accessibility_action",
        )
        Text(stringResource(R.string.auto_scroll_speed), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onSpeedChanged(AutoScrollSpeed.stepDown(speed)) },
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = decreaseSpeedDescription
                },
            ) { Text("−") }
            Text(
                AutoScrollSpeed.clamp(speed).toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("auto_scroll_speed"),
            )
            OutlinedButton(
                onClick = { onSpeedChanged(AutoScrollSpeed.stepUp(speed)) },
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = increaseSpeedDescription
                },
            ) { Text("+") }
        }
        Slider(
            value = AutoScrollSpeed.clamp(speed).toFloat(),
            onValueChange = { onSpeedChanged(it.roundToInt()) },
            valueRange = AutoScrollSpeed.MIN_LEVEL.toFloat()..AutoScrollSpeed.MAX_LEVEL.toFloat(),
            steps = AutoScrollSpeed.MAX_LEVEL - AutoScrollSpeed.MIN_LEVEL - 1,
            modifier = Modifier.fillMaxWidth().testTag("auto_scroll_speed_slider").semantics {
                contentDescription = speedDescription
                stateDescription = AutoScrollSpeed.clamp(speed).toString()
            },
        )
        Button(
            onClick = onShowControls,
            enabled = overlayAllowed && accessibilityEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("auto_scroll_show_controls"),
        ) {
            Text(stringResource(R.string.auto_scroll_show_controls))
        }
        Text(
            stringResource(R.string.auto_scroll_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    action: String,
    onClick: () -> Unit,
    tag: String,
    actionTag: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(if (granted) R.string.auto_scroll_permission_ready else R.string.auto_scroll_permission_required),
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!granted) {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.heightIn(min = 48.dp).then(
                    if (actionTag == null) Modifier else Modifier.testTag(actionTag),
                ),
            ) {
                Text(action)
            }
        }
    }
}
