package com.tuneitall.tuner

import android.Manifest
import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuneitall.tuner.ui.TunerViewModel
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import com.tuneitall.tuner.ui.theme.resolveDarkTheme
import com.tuneitall.tuner.storage.UserPreferences
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            newBase
        } else {
            localizedContext(newBase, UserPreferences(newBase).appLanguage)
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TuneItAllRoot(
                appLanguage = currentAppLanguage(),
                onAppLanguageChanged = ::setAppLanguage,
                openSupportPage = ::openSupportPage,
            )
        }
    }

    private fun currentAppLanguage(): AppLanguage {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return UserPreferences(this).appLanguage
        val localeManager = getSystemService(LocaleManager::class.java) ?: return AppLanguage.SYSTEM
        return appLanguageForTag(localeManager.applicationLocales.get(0)?.toLanguageTag())
    }

    private fun setAppLanguage(language: AppLanguage) {
        UserPreferences(this).appLanguage = language
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            recreate()
            return
        }
        val locales = language.languageTag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        val localeManager = getSystemService(LocaleManager::class.java) ?: return
        if (localeManager.applicationLocales != locales) localeManager.applicationLocales = locales
    }

    private fun openSupportPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, SUPPORT_URL.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.support_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun TuneItAllRoot(
    appLanguage: AppLanguage,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    openSupportPage: () -> Unit,
    viewModel: TunerViewModel = viewModel(),
) {
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
                appLanguage = appLanguage,
                onAppLanguageChanged = onAppLanguageChanged,
                openSupportPage = openSupportPage,
                requestMicrophonePermission = {
                    permissionRequested = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
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

private fun localizedContext(context: Context, language: AppLanguage): Context {
    val languageTag = language.languageTag ?: return context
    val locale = Locale.forLanguageTag(languageTag)
    val configuration = Configuration(context.resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return context.createConfigurationContext(configuration)
}

@Composable
internal fun UpdateSystemBarAppearance(darkTheme: Boolean) {
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    LaunchedEffect(activity, darkTheme) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
