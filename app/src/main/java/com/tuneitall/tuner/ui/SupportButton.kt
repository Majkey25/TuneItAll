package com.tuneitall.tuner.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R

@Composable
internal fun SupportButton(onSupport: () -> Unit) {
    val context = LocalContext.current
    val supportNotice = stringResource(R.string.support_app_notice)
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
        Icon(painterResource(R.drawable.ic_coffee), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.support_app))
    }
}
