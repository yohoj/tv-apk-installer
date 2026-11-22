package com.example.tvinstaller.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class ServedRequest(
    val clientIp: String,
    val path: String,
    val status: Int,
    val bytes: Long,
)

/**
 * A one-file HTTP server: the TV pulls the staged APK from this phone over the home Wi-Fi.
 * Every connection is closed after the response; the server lives only for one push.
 */
class LocalApkServer(
    private val apkFile: File,
    private val urlPath: String,
    private val onServed: (ServedRequest) -> Unit = {},
) {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "apk-http").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)

    val port: Int
        get() = serverSocket?.localPort ?: -1

    /** Binds [preferredPort] or the next free port, and returns the port actually used. */
    fun start(preferredPort: Int): Int {
        var lastError: IOException? = null
        for (candidate in preferredPort..(preferredPort + 20)) {
            val socket = ServerSocket()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(candidate))
            } catch (error: IOException) {
                lastError = error
                runCatching { socket.close() }
                continue
            }
            serverSocket = socket
            running.set(true)
            acceptThread = Thread({ acceptLoop(socket) }, "apk-http-accept").apply {
                isDaemon = true
                start()
            }
            return candidate
        }
        throw IOException(
            "端口 $preferredPort 起连续 21 个端口都无法监听：${lastError?.message ?: "未知错误"}",
        )
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        workers.shutdownNow()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                if (running.get()) continue else return
            }
            try {
                workers.execute { handle(client) }
            } catch (_: Exception) {
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { connection ->
            runCatching { connection.soTimeout = 15_000 }
            val head = readHead(connection.getInputStream()) ?: return
            val lines = head.split("\r\n")
            val requestLine = lines.firstOrNull()?.split(" ") ?: return
            if (requestLine.size < 2) return

            val method = requestLine[0].uppercase(Locale.ROOT)
            val path = requestLine[1].substringBefore('?')
            val clientIp = connection.inetAddress?.hostAddress?.removePrefix("/") ?: "?"
            val out = connection.getOutputStream()
            val total = apkFile.length()

            if (method != "GET" && method != "HEAD") {
                writeStatus(out, 405, "Method Not Allowed")
                onServed(ServedRequest(clientIp, path, 405, 0))
                return
            }
            if (!apkFile.isFile || total <= 0) {
                writeStatus(out, 500, "APK Unavailable")
                onServed(ServedRequest(clientIp, path, 500, 0))
                return
            }

            val rangeHeader = lines
                .firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
            val selection = parseRange(rangeHeader, total)
            if (selection is RangeSelection.Unsatisfiable) {
                writeStatus(out, 416, "Range Not Satisfiable")
                onServed(ServedRequest(clientIp, path, 416, 0))
                return
            }
            val range = selection as? RangeSelection.Partial
            val start = range?.start ?: 0L
            val end = range?.end ?: (total - 1)
            val length = end - start + 1
            val status = if (range == null) 200 else 206

            val header = buildString {
                append("HTTP/1.1 ").append(status).append(if (status == 200) " OK" else " Partial Content")
                append("\r\n")
                append("Content-Type: application/vnd.android.package-archive\r\n")
                append("Content-Length: ").append(length).append("\r\n")
                if (range != null) {
                    append("Content-Range: bytes ").append(start).append('-').append(end)
                        .append('/').append(total).append("\r\n")
                }
                append("Accept-Ranges: bytes\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(Charsets.ISO_8859_1)
            out.write(header)
            out.flush()

            var sent = 0L
            if (method == "GET") {
                sent = copyBody(out, start, length)
            }
            runCatching { out.flush() }
            onServed(ServedRequest(clientIp, path, status, sent))
        }
    }

    private fun copyBody(out: OutputStream, start: Long, length: Long): Long {
        var sent = 0L
        try {
            RandomAccessFile(apkFile, "r").use { file ->
                file.seek(start)
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val want = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = file.read(buffer, 0, want)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    remaining -= read
                    sent += read
                }
            }
        } catch (_: IOException) {
            // The TV dropped the connection; report what we managed to send.
        }
        return sent
    }

    private sealed interface RangeSelection {
        data object Whole : RangeSelection
        data class Partial(val start: Long, val end: Long) : RangeSelection
        data object Unsatisfiable : RangeSelection
    }

    private fun parseRange(header: String?, total: Long): RangeSelection {
        if (header.isNullOrBlank()) return RangeSelection.Whole
        if (!header.startsWith("bytes=", ignoreCase = true)) return RangeSelection.Whole
        val spec = header.substringAfter("=").substringBefore(",").trim()
        val startText = spec.substringBefore("-").trim()
        val endText = spec.substringAfter("-", "").trim()
        val start: Long
        val end: Long
        if (startText.isEmpty()) {
            val suffix = endText.toLongOrNull() ?: return RangeSelection.Unsatisfiable
            if (suffix <= 0L) return RangeSelection.Unsatisfiable
            start = (total - suffix).coerceAtLeast(0L)
            end = total - 1
        } else {
            start = startText.toLongOrNull() ?: return RangeSelection.Unsatisfiable
            end = if (endText.isEmpty()) total - 1 else (endText.toLongOrNull() ?: return RangeSelection.Unsatisfiable)
        }
        if (start < 0L || start >= total) return RangeSelection.Unsatisfiable
        val clampedEnd = end.coerceAtMost(total - 1)
        if (clampedEnd < start) return RangeSelection.Unsatisfiable
        return RangeSelection.Partial(start, clampedEnd)
    }

    private fun writeStatus(out: OutputStream, code: Int, message: String) {
        val body = "$code $message".toByteArray(Charsets.UTF_8)
        val header = (
            "HTTP/1.1 $code $message\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray(Charsets.ISO_8859_1)
        runCatching {
            out.write(header)
            out.write(body)
            out.flush()
        }
    }

    private fun readHead(input: InputStream): String? {
        val buffer = ByteArrayOutputStream(1024)
        var tail = 0
        while (buffer.size() < 16 * 1024) {
            val byte = input.read()
            if (byte < 0) return if (buffer.size() > 0) buffer.toString("ISO-8859-1") else null
            buffer.write(byte)
            tail = (tail shl 8) or byte
            if (tail == 0x0D0A0D0A) break
        }
        return buffer.toString("ISO-8859-1")
    }
}
