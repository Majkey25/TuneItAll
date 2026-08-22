package com.tuneitall.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.BuildConfig
import com.tuneitall.tuner.R

@Composable
fun AboutScreen(onBack: () -> Unit) {
    var showPrivacy by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    val textButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SecondaryHeader(stringResource(R.string.about), onBack)
        Text("TuneItAll", style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.version_value, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.copyright_notice))
        Text(stringResource(R.string.offline_privacy_summary), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.microphone_about), style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = { showPrivacy = !showPrivacy },
            colors = textButtonColors,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.privacy_policy))
        }
        if (showPrivacy) Text(stringResource(R.string.privacy_policy_full))
        TextButton(
            onClick = { showLicense = !showLicense },
            colors = textButtonColors,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.license))
        }
        if (showLicense) Text(stringResource(R.string.license_summary))
    }
}
