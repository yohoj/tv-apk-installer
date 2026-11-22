package com.example.tvinstaller.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

object ApkInspector {
    /**
     * Copies the picked document into app cache storage and reads its package name/version.
     * A copy is required because the TV downloads a real file and because the picker only
     * guarantees access through a content URI.
     */
    fun inspect(context: Context, uri: Uri): PickedApk {
        val displayName = queryDisplayName(context, uri) ?: "app.apk"
        if (!displayName.endsWith(".apk", ignoreCase = true)) {
            throw IOException("请选择 .apk 文件（不支持 .apks / .xapk 分割包）")
        }

        val staging = File(context.cacheDir, "apk-staging")
        if (staging.exists()) staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("无法创建临时目录")

        val target = File(staging, "staged.apk")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink) }
        }
        if (target.length() <= 0L) throw IOException("所选文件是空的")

        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(target.absolutePath, 0)
            ?: throw IOException("无法解析 APK 的包信息，可能文件已损坏")
        val appInfo = info.applicationInfo ?: throw IOException("APK 缺少 application 信息")
        appInfo.sourceDir = target.absolutePath
        appInfo.publicSourceDir = target.absolutePath

        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: displayName.removeSuffix(".apk")

        return PickedApk(
            file = target,
            displayName = displayName,
            packageName = info.packageName,
            versionCode = versionCodeOf(info),
            label = label,
            sizeBytes = target.length(),
            md5 = Md5.ofFile(target),
        )
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index < 0) null else cursor.getString(index)
                }
        }.getOrNull()
    }
}
