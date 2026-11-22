package com.example.tvinstaller.core

import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * The payload the TV needs in order to pull and install an APK from this phone.
 *
 * Field order is fixed by TCL's 多屏互动 (T-Cast) protocol:
 * `223>>INSTALL>>package>>label>>url>>2>>versionCode>>sizeMB>>appMd5>>signatureMd5>>icon>>apkPathMd5`
 */
data class InstallRequest(
    val packageName: String,
    val label: String,
    val url: String,
    val versionCode: Long,
    val sizeMb: Int,
    val apkPathMd5: String,
)

sealed interface TcastEvent {
    data object Connected : TcastEvent
    data object AwaitingConfirm : TcastEvent
    data class Progress(val stage: Int, val percent: Int) : TcastEvent
    data object Installed : TcastEvent
    data object Denied : TcastEvent
    data class Failed(val detail: String?) : TcastEvent
    data class Unknown(val raw: String) : TcastEvent
}

enum class TcastResult { INSTALLED, DENIED, FAILED, TIMEOUT, CANCELLED }

/**
 * Speaks the T-Cast install protocol on TCP 6553: length-prefixed frames of `>>`-separated UTF-8
 * text, a `150>>` heartbeat every five seconds, and the TV's own download of [InstallRequest.url].
 */
class TcastClient(
    private val host: String,
    private val port: Int = PORT,
    private val connectTimeoutMs: Int = 5_000,
) {
    fun install(
        request: InstallRequest,
        timeoutSeconds: Int,
        isActive: () -> Boolean,
        onEvent: (TcastEvent) -> Unit,
    ): TcastResult {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = 1_000
            onEvent(TcastEvent.Connected)

            val out = socket.getOutputStream()
            val input = socket.getInputStream()
            val frames = FrameBuffer()

            send(out, OPEN_SESSION)
            send(out, installFrame(request))

            var lastHeartbeat = System.currentTimeMillis()
            val deadline = lastHeartbeat + timeoutSeconds * 1_000L
            val chunk = ByteArray(16 * 1024)

            while (System.currentTimeMillis() < deadline) {
                if (!isActive()) return TcastResult.CANCELLED
                var read: Int
                try {
                    read = input.read(chunk)
                } catch (_: SocketTimeoutException) {
                    read = 0
                }
                if (read < 0) {
                    // The TV closes the tunnel once it is done with us.
                    return TcastResult.FAILED
                }
                if (read > 0) {
                    frames.feed(chunk, read)
                    while (true) {
                        val frame = frames.poll() ?: break
                        handleFrame(frame, request.packageName, onEvent)?.let { return it }
                    }
                }
                val now = System.currentTimeMillis()
                if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                    send(out, HEARTBEAT)
                    lastHeartbeat = now
                }
            }
            return TcastResult.TIMEOUT
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun handleFrame(
        frame: String,
        packageName: String,
        onEvent: (TcastEvent) -> Unit,
    ): TcastResult? {
        val parts = frame.split(SEPARATOR)
        if (parts.size < 5 || parts[0] != TAG || parts[2] != packageName) {
            onEvent(TcastEvent.Unknown(frame))
            return null
        }
        when (parts[1]) {
            "INSTALL" -> when (parts[4]) {
                "confirm_pending" -> onEvent(TcastEvent.AwaitingConfirm)
                "denied" -> {
                    onEvent(TcastEvent.Denied)
                    return TcastResult.DENIED
                }
                "installed" -> {
                    onEvent(TcastEvent.Installed)
                    return TcastResult.INSTALLED
                }
                "installing" -> onEvent(TcastEvent.Progress(STAGE_INSTALLING, 100))
                "failed" -> {
                    onEvent(TcastEvent.Failed(parts.getOrNull(5)))
                    return TcastResult.FAILED
                }
                else -> onEvent(TcastEvent.Unknown(frame))
            }

            "PROGRESS" -> {
                val stage = parts[4].toIntOrNull() ?: -1
                val percent = parts[5].toIntOrNull() ?: -1
                when (stage) {
                    STAGE_INSTALLED -> {
                        onEvent(TcastEvent.Installed)
                        return TcastResult.INSTALLED
                    }
                    STAGE_FAILED -> {
                        onEvent(TcastEvent.Failed(parts.getOrNull(6)))
                        return TcastResult.FAILED
                    }
                    else -> onEvent(TcastEvent.Progress(stage, percent))
                }
            }

            else -> onEvent(TcastEvent.Unknown(frame))
        }
        return null
    }

    private fun installFrame(request: InstallRequest): String = listOf(
        TAG,
        "INSTALL",
        request.packageName,
        sanitize(request.label),
        request.url,
        "2",
        request.versionCode.toString(),
        request.sizeMb.toString(),
        "",
        "",
        "",
        request.apkPathMd5,
    ).joinToString(SEPARATOR)

    private fun send(out: OutputStream, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val size = body.size
        out.write(
            byteArrayOf(
                (size ushr 24).toByte(),
                (size ushr 16).toByte(),
                (size ushr 8).toByte(),
                size.toByte(),
            ),
        )
        out.write(body)
        out.flush()
    }

    /** Keeps our own `>>` separators out of a display label. */
    private fun sanitize(label: String): String =
        label.replace(SEPARATOR, " ").replace("\n", " ").trim()

    private class FrameBuffer {
        private var buffer = ByteArray(16 * 1024)
        private var length = 0

        fun feed(chunk: ByteArray, count: Int) {
            if (length + count > buffer.size) {
                var capacity = buffer.size
                while (capacity < length + count) capacity *= 2
                buffer = buffer.copyOf(capacity)
            }
            System.arraycopy(chunk, 0, buffer, length, count)
            length += count
        }

        fun poll(): String? {
            if (length < 4) return null
            val size = ((buffer[0].toInt() and 0xff) shl 24) or
                ((buffer[1].toInt() and 0xff) shl 16) or
                ((buffer[2].toInt() and 0xff) shl 8) or
                (buffer[3].toInt() and 0xff)
            if (size < 0 || size > MAX_FRAME) throw IOException("非法报文长度 $size")
            if (length < 4 + size) return null
            val text = String(buffer, 4, size, Charsets.UTF_8)
            System.arraycopy(buffer, 4 + size, buffer, 0, length - 4 - size)
            length -= 4 + size
            return text
        }
    }

    companion object {
        const val PORT = 6553
        private const val TAG = "223"
        private const val SEPARATOR = ">>"
        private const val OPEN_SESSION = "159>>1>>0"
        private const val HEARTBEAT = "150>>"
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val MAX_FRAME = 1 shl 20

        const val STAGE_DOWNLOADING = 1
        const val STAGE_FAILED = 2
        const val STAGE_INSTALLING = 3
        const val STAGE_INSTALLED = 5
    }
}
