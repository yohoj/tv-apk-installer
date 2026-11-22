package com.example.tvinstaller.core

import java.io.File

/** A local APK that has been copied into app storage and inspected. */
data class PickedApk(
    val file: File,
    val displayName: String,
    val packageName: String,
    val versionCode: Long,
    val label: String,
    val sizeBytes: Long,
    val md5: String,
)

enum class PushPhase {
    /** Nothing running. */
    IDLE,

    /** Reading the APK and preparing the local download server. */
    PREPARING,

    /** Opening the TCP session to the TV on port 6553. */
    CONNECTING,

    /** The TV has been asked to install and is waiting for the remote-control "同意". */
    WAITING_CONFIRM,

    /** The TV is pulling the APK over HTTP. */
    DOWNLOADING,

    /** The TV reports that it is installing. */
    INSTALLING,

    SUCCESS,
    FAILED,
}

enum class LogLevel { INFO, STEP, TV, SUCCESS, WARN, ERROR }

data class LogLine(
    val timeMillis: Long,
    val text: String,
    val level: LogLevel = LogLevel.INFO,
)

data class PushState(
    val phase: PushPhase = PushPhase.IDLE,
    val apk: PickedApk? = null,
    val tvHost: String = "",
    val downloadUrl: String? = null,
    /** 0..100 while the TV downloads/installs, null when unknown. */
    val percent: Int? = null,
    /** Short headline shown next to the progress indicator. */
    val message: String = "",
    /** Extra detail for the failure card. */
    val error: String? = null,
    val log: List<LogLine> = emptyList(),
    val startedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
) {
    val isRunning: Boolean
        get() = phase == PushPhase.PREPARING ||
            phase == PushPhase.CONNECTING ||
            phase == PushPhase.WAITING_CONFIRM ||
            phase == PushPhase.DOWNLOADING ||
            phase == PushPhase.INSTALLING

    val isFinished: Boolean
        get() = phase == PushPhase.SUCCESS || phase == PushPhase.FAILED
}

/** Everything the screen renders. */
data class UiState(
    val push: PushState = PushState(),
    val tvIp: String = "",
    val apk: PickedApk? = null,
    /** Wi-Fi IPv4 address of this phone, or null when there is no LAN address. */
    val localIp: String? = null,
    /** An APK is being copied/inspected. */
    val isPickingApk: Boolean = false,
    /** One-shot message for the snackbar/banner: pick errors, invalid IP, etc. */
    val uiError: String? = null,
)
