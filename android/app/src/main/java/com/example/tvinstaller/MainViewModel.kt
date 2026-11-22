package com.example.tvinstaller

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvinstaller.core.ApkInspector
import com.example.tvinstaller.core.NetworkUtils
import com.example.tvinstaller.core.PickedApk
import com.example.tvinstaller.core.PushEngine
import com.example.tvinstaller.core.PushService
import com.example.tvinstaller.core.SettingsStore
import com.example.tvinstaller.core.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsStore(application)

    private val tvIp = MutableStateFlow(settings.tvIp)
    private val apk = MutableStateFlow<PickedApk?>(null)
    private val localIp = MutableStateFlow<String?>(null)
    private val isPicking = MutableStateFlow(false)
    private val uiError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState> = combine(
        PushEngine.state,
        tvIp,
        apk,
        localIp,
        combine(isPicking, uiError) { picking, error -> picking to error },
    ) { push, ip, picked, lan, pickingState ->
        UiState(
            push = push,
            tvIp = ip,
            apk = picked,
            localIp = lan,
            isPickingApk = pickingState.first,
            uiError = pickingState.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(tvIp = settings.tvIp),
    )

    init {
        refreshLanIp()
    }

    fun refreshLanIp() {
        viewModelScope.launch {
            localIp.value = withContext(Dispatchers.IO) { NetworkUtils.preferredLanIp() }
        }
    }

    fun onTvIpChange(value: String) {
        tvIp.value = value.take(45)
    }

    fun onApkPicked(uri: Uri) {
        if (isPicking.value) return
        isPicking.value = true
        uiError.value = null
        viewModelScope.launch {
            try {
                val picked = withContext(Dispatchers.IO) { ApkInspector.inspect(getApplication(), uri) }
                apk.value = picked
                PushEngine.reset()
                refreshLanIp()
            } catch (error: Throwable) {
                uiError.value = error.message ?: "读取 APK 失败"
            } finally {
                isPicking.value = false
            }
        }
    }

    fun startPush() {
        if (PushEngine.state.value.isRunning) return
        val picked = apk.value
        if (picked == null) {
            uiError.value = "请先选择一个 APK 文件"
            return
        }
        val host = normalizeHost(tvIp.value)
        if (!isIpv4(host)) {
            uiError.value = "电视 IP 格式不对，例如 192.168.3.212"
            return
        }
        uiError.value = null
        settings.tvIp = host
        tvIp.value = host
        refreshLanIp()
        PushEngine.reset()
        PushEngine.start(
            apk = picked,
            tvHost = host,
            httpPort = settings.httpPort,
            timeoutSeconds = settings.timeoutSeconds,
        )
        PushService.start(getApplication(), picked.label, host)
    }

    fun cancelPush() {
        PushEngine.cancel()
        PushService.stop(getApplication())
    }

    fun resetPush() {
        PushEngine.reset()
    }

    fun dismissError() {
        uiError.value = null
    }

    private fun normalizeHost(raw: String): String =
        raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')

    private fun isIpv4(value: String): Boolean {
        val parts = value.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                (part.toIntOrNull() ?: -1) in 0..255
        }
    }
}
