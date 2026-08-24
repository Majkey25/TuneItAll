package com.tuneitall.tuner.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.BuildConfig
import com.tuneitall.tuner.R

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onSupport: () -> Unit,
) {
    val context = LocalContext.current
    val supportNotice = stringResource(R.string.support_app_notice)
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
        SecondaryHeader(stringResource(R.string.app_details), onBack)
        Text("TuneItAll", style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.version_value, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.copyright_notice))
        Text(stringResource(R.string.offline_privacy_summary), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.microphone_about), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.headstock_icon_attribution), style = MaterialTheme.typography.bodySmall)
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
        Button(
            onClick = {
                Toast.makeText(context, supportNotice, Toast.LENGTH_SHORT).show()
                onSupport()
            },
            border = BorderStroke(1.dp, Color(0xFF111111)),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFDD00),
                contentColor = Color(0xFF111111),
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_coffee),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.support_app))
        }
    }
}
