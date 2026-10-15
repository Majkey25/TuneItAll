package com.tuneitall.tuner.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp).testTag("settings_icon"))
}

@Composable
internal fun SettingLabel(
    label: String,
    @DrawableRes icon: Int,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Text(label, style = style, modifier = Modifier.weight(1f))
    }
}
