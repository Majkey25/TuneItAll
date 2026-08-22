package com.tuneitall.tuner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuneitall.tuner.ui.TunerViewModel
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import com.tuneitall.tuner.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TuneItAllRoot()
        }
    }
}

@Composable
private fun TuneItAllRoot(viewModel: TunerViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = requireNotNull(LocalActivity.current)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(
            granted = granted,
            permanentlyDenied = !granted && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
        )
    }
    val darkTheme = resolveDarkTheme(state.themeMode, isSystemInDarkTheme())

    UpdateSystemBarAppearance(darkTheme)
    TuneItAllTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LifecycleStartEffect(viewModel) {
                viewModel.onStart()
                onStopOrDispose { viewModel.onStop() }
            }
            LaunchedEffect(Unit) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                viewModel.onPermissionResult(granted, permanentlyDenied = false)
                if (!granted && !permissionRequested) {
                    permissionRequested = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            TuneItAllApp(
                state = state,
                viewModel = viewModel,
                openApplicationSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
internal fun UpdateSystemBarAppearance(darkTheme: Boolean) {
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    SideEffect {
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
