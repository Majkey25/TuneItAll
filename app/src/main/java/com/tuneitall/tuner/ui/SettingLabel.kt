package com.tuneitall.tuner.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.ui.theme.mutedBackgroundColor

@Composable
internal fun SettingIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp).testTag("settings_icon"))
}

@Composable
internal fun SettingsDisclosure(
    label: String,
    @DrawableRes icon: Int,
    tag: String,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(if (expanded) R.string.section_expanded else R.string.section_collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
            supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
            leadingContent = { SettingIcon(icon) },
            trailingContent = {
                Icon(painterResource(R.drawable.ic_expand_more), null,
                    Modifier.size(24.dp).rotate(if (expanded) 180f else 0f))
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background,
                headlineColor = MaterialTheme.colorScheme.onBackground,
                supportingColor = mutedBackgroundColor(),
                leadingIconColor = MaterialTheme.colorScheme.onBackground,
                trailingIconColor = MaterialTheme.colorScheme.onBackground,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = description }.testTag(tag),
        )
        if (expanded) content()
    }
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
