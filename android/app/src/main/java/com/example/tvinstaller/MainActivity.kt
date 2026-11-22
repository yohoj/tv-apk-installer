package com.example.tvinstaller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tvinstaller.ui.TvInstallerScreen
import com.example.tvinstaller.ui.theme.TvInstallerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onApkPicked(uri)
    }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TvInstallerTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                KeepScreenOn(enabled = state.push.isRunning)
                Box {
                    TvInstallerScreen(
                        state = state,
                        onTvIpChange = viewModel::onTvIpChange,
                        onPickApk = ::launchApkPicker,
                        onPush = {
                            ensureNotificationPermission()
                            viewModel.startPush()
                        },
                        onCancel = viewModel::cancelPush,
                        onReset = viewModel::resetPush,
                        onDismissError = viewModel::dismissError,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLanIp()
    }

    private fun launchApkPicker() {
        pickApk.launch(
            arrayOf(
                "application/vnd.android.package-archive",
                "application/octet-stream",
            ),
        )
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
