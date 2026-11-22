package com.example.tvinstaller.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import kotlin.math.max

/**
 * Runs one push at a time: stage the APK, serve it over HTTP, drive the T-Cast session,
 * and publish every step as [state] for the UI and the foreground notification.
 */
object PushEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PushState())
    val state: StateFlow<PushState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(apk: PickedApk, tvHost: String, httpPort: Int, timeoutSeconds: Int) {
        if (job?.isActive == true) return
        val host = tvHost.trim()
        _state.value = PushState(
            phase = PushPhase.PREPARING,
            apk = apk,
            tvHost = host,
            message = "正在准备推送…",
            startedAtMillis = System.currentTimeMillis(),
        )
        appendLog("准备推送 ${apk.label}（${apk.packageName} v${apk.versionCode}）", LogLevel.STEP)
        job = scope.launch { runPush(apk, host, httpPort, timeoutSeconds) }
    }

    fun cancel() {
        if (job?.isActive != true) return
        appendLog("已取消推送", LogLevel.WARN)
        job?.cancel()
        job = null
        _state.update { current ->
            if (current.isFinished) current
            else current.copy(
                phase = PushPhase.IDLE,
                message = "已取消",
                percent = null,
                finishedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    /** Clears a finished or idle session; keeps nothing but the log history of this run. */
    fun reset() {
        if (job?.isActive == true) return
        _state.value = PushState()
    }

    private suspend fun runPush(apk: PickedApk, host: String, httpPort: Int, timeoutSeconds: Int) {
        var server: LocalApkServer? = null
        val self = currentCoroutineContext()
        try {
            if (host.isBlank()) throw IOException("请先填写电视 IP 地址")

            val localIp = NetworkUtils.preferredLanIp()
                ?: throw IOException("手机没有局域网 IPv4 地址，请先连接与电视同一个 Wi-Fi")
            appendLog("手机局域网地址：$localIp", LogLevel.STEP)

            val path = "/${apk.packageName}-${apk.versionCode}.apk"
            server = LocalApkServer(apk.file, path) { served ->
                val message = "${served.clientIp} → ${served.path} · HTTP ${served.status} · ${formatBytes(served.bytes)}"
                if (served.status in 200..299 && served.bytes > 0) {
                    appendLog(message, LogLevel.TV)
                } else if (served.status !in 200..299) {
                    appendLog(message, LogLevel.WARN)
                }
            }
            val actualPort = server.start(httpPort)
            val url = "http://$localIp:$actualPort$path"
            _state.update { it.copy(downloadUrl = url, message = "下载服务已就绪，正在连接电视…") }
            appendLog("下载服务：$url", LogLevel.STEP)
            if (apk.sizeBytes > 0) {
                appendLog("APK 大小 ${formatBytes(apk.sizeBytes)} · MD5 ${apk.md5}", LogLevel.INFO)
            }

            updatePhase(PushPhase.CONNECTING, "正在连接电视 $host:${TcastClient.PORT}…")
            appendLog("连接 $host:${TcastClient.PORT}…", LogLevel.STEP)
            if (!NetworkUtils.isPortOpen(host, TcastClient.PORT)) {
                throw IOException(
                    "连不上 $host:${TcastClient.PORT}。请确认电视已开机、手机与电视在同一 Wi-Fi、路由器没有开启 AP 隔离",
                )
            }

            val request = InstallRequest(
                packageName = apk.packageName,
                label = apk.label,
                url = url,
                versionCode = apk.versionCode,
                sizeMb = max(1, ((apk.sizeBytes + 1_048_575L) / 1_048_576L).toInt()),
                apkPathMd5 = Md5.of(url),
            )
            val result = TcastClient(host).install(
                request = request,
                timeoutSeconds = timeoutSeconds,
                isActive = { self.isActive },
                onEvent = ::handleEvent,
            )

            when (result) {
                TcastResult.INSTALLED -> succeed()
                TcastResult.DENIED -> fail("电视上选择了「拒绝」。可以再推送一次，并在电视上按「右」→「同意」→「确认」")
                TcastResult.FAILED -> fail(
                    "电视没能下载或安装这个 APK。请确认：APK 是单文件 .apk、手机与电视网络互通、" +
                        "电视上没有同包名但签名不同的旧版本",
                )
                TcastResult.TIMEOUT -> fail("等待超时（$timeoutSeconds 秒）。电视没有完成安装，可以重试或调大超时时间")
                TcastResult.CANCELLED -> Unit
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            fail(error.message ?: "推送失败：${error.javaClass.simpleName}")
        } finally {
            server?.stop()
            if (server != null) appendLog("本地下载服务已停止", LogLevel.INFO)
        }
    }

    private fun handleEvent(event: TcastEvent) {
        when (event) {
            TcastEvent.Connected -> {
                appendLog("已连接电视，正在发起推送请求", LogLevel.STEP)
                updatePhase(PushPhase.CONNECTING, "已连接电视，正在发起推送…")
            }

            TcastEvent.AwaitingConfirm -> {
                appendLog("电视已弹出「多屏互动：正在发起应用推送」", LogLevel.TV)
                updatePhase(
                    PushPhase.WAITING_CONFIRM,
                    "请拿起遥控器：按「右」选中「同意」，再按「确认/OK」",
                    percent = 0,
                )
            }

            is TcastEvent.Progress -> when (event.stage) {
                TcastClient.STAGE_DOWNLOADING -> {
                    if (_state.value.phase != PushPhase.DOWNLOADING) {
                        appendLog("电视开始下载 APK", LogLevel.TV)
                    }
                    updatePhase(
                        PushPhase.DOWNLOADING,
                        "电视正在下载 APK" + event.percentLabel(),
                        percent = event.percent.takeIf { it in 0..100 } ?: _state.value.percent,
                    )
                }

                TcastClient.STAGE_INSTALLING -> {
                    if (_state.value.phase != PushPhase.INSTALLING) {
                        appendLog("电视已下载完成，正在安装", LogLevel.TV)
                    }
                    updatePhase(
                        PushPhase.INSTALLING,
                        "电视正在安装 APK",
                        percent = 100,
                    )
                }

                else -> appendLog("电视进度：stage=${event.stage} percent=${event.percent}", LogLevel.TV)
            }

            TcastEvent.Installed -> succeed()

            TcastEvent.Denied -> fail("电视上选择了「拒绝」")

            is TcastEvent.Failed -> fail(event.detail ?: "电视报告安装失败")

            is TcastEvent.Unknown -> {
                val text = event.raw.replace("\n", " ").take(300)
                if (text.isNotBlank()) appendLog("电视：$text", LogLevel.TV)
            }
        }
    }

    private fun succeed() {
        val current = _state.value
        if (current.phase == PushPhase.SUCCESS) return
        appendLog("电视报告安装成功", LogLevel.SUCCESS)
        _state.value = current.copy(
            phase = PushPhase.SUCCESS,
            percent = 100,
            message = "安装成功",
            error = null,
            finishedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun fail(reason: String) {
        val current = _state.value
        if (current.isFinished) return
        appendLog(reason, LogLevel.ERROR)
        _state.value = current.copy(
            phase = PushPhase.FAILED,
            message = "推送失败",
            error = reason,
            percent = null,
            finishedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun updatePhase(phase: PushPhase, message: String, percent: Int? = _state.value.percent) {
        _state.update { current ->
            if (current.isFinished) current
            else current.copy(phase = phase, message = message, percent = percent)
        }
    }

    private fun appendLog(text: String, level: LogLevel) {
        val line = LogLine(System.currentTimeMillis(), text, level)
        _state.update { current -> current.copy(log = (current.log + line).takeLast(MAX_LOG_LINES)) }
    }

    private fun TcastEvent.Progress.percentLabel(): String =
        if (percent in 0..100) " · $percent%" else ""

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    private const val MAX_LOG_LINES = 400
}
