package com.example.tvinstaller.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tvinstaller.core.LogLevel
import com.example.tvinstaller.core.LogLine
import com.example.tvinstaller.core.PickedApk
import com.example.tvinstaller.core.PushPhase
import com.example.tvinstaller.core.PushState
import com.example.tvinstaller.core.UiState
import com.example.tvinstaller.ui.theme.MonoBody
import com.example.tvinstaller.ui.theme.MonoLabel
import com.example.tvinstaller.ui.theme.TvInstallerTheme
import com.example.tvinstaller.ui.theme.tv
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun TvInstallerScreen(
    state: UiState,
    onTvIpChange: (String) -> Unit,
    onPickApk: () -> Unit,
    onPush: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onDismissError: () -> Unit,
) {
    val push = state.push
    val busy = push.isRunning
    val elapsed = rememberElapsed(push)
    val scheme = MaterialTheme.colorScheme
    val uiError = state.uiError

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        // A single soft accent wash at the top; the rest of the page stays quiet.
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.primary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            ScreenHeader(phase = push.phase)
            Spacer(Modifier.height(18.dp))

            if (uiError != null) {
                ErrorBanner(message = uiError, onDismiss = onDismissError)
                Spacer(Modifier.height(12.dp))
            }

            if (push.phase == PushPhase.IDLE) {
                ApkPanel(
                    state = state,
                    busy = busy,
                    onPickApk = onPickApk,
                )

                Spacer(Modifier.height(14.dp))

                TvPanel(
                    state = state,
                    busy = busy,
                    onTvIpChange = onTvIpChange,
                )
            } else {
                TargetStrip(
                    apk = state.apk,
                    host = push.tvHost.ifBlank { state.tvIp },
                    hasLan = state.localIp != null,
                )
            }

            Spacer(Modifier.height(16.dp))

            StatusZone(
                state = state,
                elapsedMillis = elapsed,
            )

            Spacer(Modifier.height(16.dp))

            ActionZone(
                state = state,
                onPush = onPush,
                onCancel = onCancel,
                onReset = onReset,
            )

            if (push.log.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LogPanel(lines = push.log)
            }

            Spacer(Modifier.height(34.dp))
        }
    }
}

/* ---------------------------------------------------------------------------
 * Header
 * ------------------------------------------------------------------------- */

@Composable
private fun ScreenHeader(phase: PushPhase) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                val barColor = scheme.onPrimary
                Canvas(Modifier.size(20.dp)) {
                    val gap = size.width * 0.15f
                    val barWidth = (size.width - gap * 2f) / 3f
                    val heights = floatArrayOf(0.34f, 0.62f, 1f)
                    heights.forEachIndexed { index, factor ->
                        val barHeight = size.height * factor
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "电视 APK 安装助手",
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "T-Cast 多屏互动 · 同一 Wi-Fi",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            PhasePill(phase = phase)
        }
    }
}

@Composable
private fun PhasePill(phase: PushPhase) {
    val palette = MaterialTheme.tv
    val scheme = MaterialTheme.colorScheme
    val tones = when (phase) {
        PushPhase.IDLE -> scheme.onSurfaceVariant to scheme.surfaceVariant
        PushPhase.WAITING_CONFIRM -> palette.waiting to palette.waitingContainer
        PushPhase.SUCCESS -> scheme.primary to scheme.primaryContainer
        PushPhase.FAILED -> palette.danger to palette.dangerContainer
        else -> scheme.primary to scheme.primaryContainer.copy(alpha = 0.65f)
    }
    Pill(
        text = phaseStatusLabel(phase),
        color = tones.first,
        container = tones.second,
        dot = true,
    )
}

/* ---------------------------------------------------------------------------
 * Error banner
 * ------------------------------------------------------------------------- */

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val palette = MaterialTheme.tv
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.dangerContainer)
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = palette.danger,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onDangerContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭提示",
                tint = palette.danger,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/* ---------------------------------------------------------------------------
 * APK selection
 * ------------------------------------------------------------------------- */

@Composable
private fun ApkPanel(
    state: UiState,
    busy: Boolean,
    onPickApk: () -> Unit,
) {
    val apk = state.apk
    Panel {
        SectionHeader(index = "01", title = "安装包")
        Spacer(Modifier.height(14.dp))

        if (apk == null) {
            EmptyApkZone(
                isPicking = state.isPickingApk,
                enabled = !busy,
                onPickApk = onPickApk,
            )
        } else {
            Row(verticalAlignment = Alignment.Top) {
                ApkBadge()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = apk.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = apk.packageName,
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetaChip(text = "版本 ${apk.versionCode}")
                        MetaChip(text = formatApkSize(apk.sizeBytes))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = apk.displayName,
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onPickApk,
                    enabled = !busy && !state.isPickingApk,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("更换", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (state.isPickingApk) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在读取新的安装包…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApkBadge() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "APK",
            style = MonoLabel,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text = text,
        style = MonoLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyApkZone(
    isPicking: Boolean,
    enabled: Boolean,
    onPickApk: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val outline = MaterialTheme.tv.hairline
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceVariant.copy(alpha = 0.45f), shape)
            .drawBehind {
                drawRoundRect(
                    color = outline.copy(alpha = 0.75f),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f),
                    ),
                )
            }
            .clip(shape)
            .clickable(
                enabled = enabled && !isPicking,
                role = Role.Button,
                onClickLabel = "选择 APK 文件",
                onClick = onPickApk,
            )
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (isPicking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isPicking) "正在读取安装包…" else "选择一个 APK 文件",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "从手机文件里挑选，无需 Root",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/* ---------------------------------------------------------------------------
 * TV target
 * ------------------------------------------------------------------------- */

@Composable
private fun TvPanel(
    state: UiState,
    busy: Boolean,
    onTvIpChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = MaterialTheme.tv
    val focusManager = LocalFocusManager.current
    val localIp = state.localIp

    Panel {
        SectionHeader(index = "02", title = "电视")
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = state.tvIp,
            onValueChange = onTvIpChange,
            modifier = Modifier.fillMaxWidth(),
            readOnly = busy,
            singleLine = true,
            label = { Text("电视 IP 地址") },
            placeholder = {
                Text(
                    text = "192.168.3.212",
                    style = MonoBody,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (!busy && state.tvIp.isNotEmpty()) {
                    IconButton(onClick = { onTvIpChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清空电视 IP",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            textStyle = MonoBody,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = palette.hairline,
                focusedLabelColor = scheme.primary,
                unfocusedLabelColor = scheme.onSurfaceVariant,
                cursorColor = scheme.primary,
            ),
        )

        Spacer(Modifier.height(12.dp))

        if (localIp == null) {
            NoLanStrip()
        } else {
            LanStrip(ip = localIp)
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "电视 IP 可在电视「设置 → 网络 → 网络信息」里查看。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanStrip(ip: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "本机 ",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = ip,
            style = MonoBody,
            color = scheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "与电视同一网络",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun NoLanStrip() {
    val palette = MaterialTheme.tv
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.waitingContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = palette.waiting,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "手机没有连上 Wi-Fi",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onWaitingContainer,
            )
            Text(
                text = "请先连接与电视相同的 Wi-Fi，否则电视无法下载安装包。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onWaitingContainer.copy(alpha = 0.8f),
            )
        }
    }
}

/* ---------------------------------------------------------------------------
 * Compact target strip (shown while a push is active or finished)
 * ------------------------------------------------------------------------- */

@Composable
private fun TargetStrip(
    apk: PickedApk?,
    host: String,
    hasLan: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = MaterialTheme.tv

    Panel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "APK",
                    style = MonoLabel,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = apk?.label ?: "未选择安装包",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = host.ifBlank { "电视 IP 未填写" },
                        style = MonoLabel,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (apk != null) {
                        Text(
                            text = "  ·  ${formatApkSize(apk.sizeBytes)}",
                            style = MonoLabel,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (!hasLan) {
                Spacer(Modifier.width(8.dp))
                Pill(
                    text = "无 Wi-Fi",
                    color = palette.onWaitingContainer,
                    container = palette.waitingContainer,
                    dot = true,
                    dotColor = palette.waiting,
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * Status
 * ------------------------------------------------------------------------- */

@Composable
private fun StatusZone(state: UiState, elapsedMillis: Long) {
    val push = state.push
    val apk = state.apk ?: push.apk
    val host = push.tvHost.ifBlank { state.tvIp }

    when (push.phase) {
        PushPhase.IDLE -> Unit
        PushPhase.WAITING_CONFIRM -> WaitingCard(
            host = host,
            elapsedMillis = elapsedMillis,
        )
        PushPhase.SUCCESS -> SuccessCard(
            apkLabel = apk?.label.orEmpty(),
            host = host,
            elapsedMillis = elapsedMillis,
        )
        PushPhase.FAILED -> ErrorCard(
            error = push.error,
            apkLabel = apk?.label,
        )
        else -> ProgressCard(
            phase = push.phase,
            message = push.message,
            percent = push.percent,
            elapsedMillis = elapsedMillis,
            downloadUrl = push.downloadUrl,
        )
    }
}

/* ---------------------------------------------------------------------------
 * Actions
 * ------------------------------------------------------------------------- */

@Composable
private fun ActionZone(
    state: UiState,
    onPush: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
) {
    val push = state.push
    val scheme = MaterialTheme.colorScheme
    val palette = MaterialTheme.tv
    val apk = state.apk

    when (push.phase) {
        PushPhase.SUCCESS -> {
            Button(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("再装一个", style = MaterialTheme.typography.labelLarge)
            }
        }

        PushPhase.FAILED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPush,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("重试推送", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, palette.hairline),
                ) {
                    Text("重新开始", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        PushPhase.IDLE -> {
            val canPush = apk != null && !state.isPickingApk
            Button(
                onClick = onPush,
                enabled = canPush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                    disabledContainerColor = scheme.surfaceVariant,
                    disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                ),
            ) {
                if (state.isPickingApk) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.isPickingApk) "读取中…" else "推送安装",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(10.dp))
            HintLine(
                text = when {
                    state.isPickingApk -> "正在读取安装包信息…"
                    apk == null -> "先选择一个 APK 文件"
                    state.localIp == null -> "手机还没连上 Wi-Fi，推送可能失败"
                    state.tvIp.isBlank() -> "填写电视 IP 后即可推送"
                    else -> "将把「${apk?.label ?: "安装包"}」推送到 ${state.tvIp}"
                },
            )
        }

        else -> {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (push.phase == PushPhase.WAITING_CONFIRM) {
                        palette.waiting.copy(alpha = 0.55f)
                    } else {
                        palette.hairline
                    },
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (push.phase == PushPhase.WAITING_CONFIRM) "取消等待" else "取消推送",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "取消只会停止手机端的等待，不会影响电视上已经装好的应用。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HintLine(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/* ---------------------------------------------------------------------------
 * Elapsed timer
 * ------------------------------------------------------------------------- */

@Composable
private fun rememberElapsed(push: PushState): Long {
    val started = push.startedAtMillis
    var now by remember(started) { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(started, push.isRunning) {
        if (started == null) return@LaunchedEffect
        if (!push.isRunning) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val finished = push.finishedAtMillis
    if (started == null) return 0L
    return if (finished != null) {
        (finished - started).coerceAtLeast(0L)
    } else {
        (now - started).coerceAtLeast(0L)
    }
}

/* ---------------------------------------------------------------------------
 * Previews (fabricated data only)
 * ------------------------------------------------------------------------- */

private fun sampleApk(): PickedApk = PickedApk(
    file = File("/storage/emulated/0/Download/livingroom_tv_2.4.1.apk"),
    displayName = "livingroom_tv_2.4.1.apk",
    packageName = "com.example.livingroom.tv",
    versionCode = 241L,
    label = "客厅影院",
    sizeBytes = 18_743_296L,
    md5 = "9f2c6e1b7a4d0c58f3b2a1e6d4c7b8a9",
)

private fun sampleLog(failed: Boolean = false): List<LogLine> {
    val now = System.currentTimeMillis()
    val lines = mutableListOf(
        LogLine(now - 46_000L, "准备安装包，计算 MD5 校验值", LogLevel.STEP),
        LogLine(now - 45_000L, "本机地址 192.168.3.24 · 下载端口 8080", LogLevel.INFO),
        LogLine(now - 44_000L, "连接电视 192.168.3.212:6553", LogLevel.STEP),
        LogLine(now - 43_000L, "TV: 多屏互动通道就绪", LogLevel.TV),
        LogLine(now - 23_000L, "已发送安装请求，等待电视确认", LogLevel.WARN),
    )
    if (failed) {
        lines += LogLine(
            now - 2_000L,
            "连接被拒绝：Connection refused (192.168.3.212:6553)",
            LogLevel.ERROR,
        )
    }
    return lines
}

@Composable
private fun PreviewHost(state: UiState, darkTheme: Boolean = false) {
    TvInstallerTheme(darkTheme = darkTheme) {
        TvInstallerScreen(
            state = state,
            onTvIpChange = {},
            onPickApk = {},
            onPush = {},
            onCancel = {},
            onReset = {},
            onDismissError = {},
        )
    }
}

@Preview(name = "IDLE · 已选安装包", showBackground = true, widthDp = 380, heightDp = 940)
@Composable
private fun PreviewIdle() {
    val apk = sampleApk()
    PreviewHost(
        state = UiState(
            push = PushState(apk = apk),
            tvIp = "192.168.3.212",
            apk = apk,
            localIp = "192.168.3.24",
        ),
    )
}

@Preview(name = "IDLE · 空状态与无 Wi-Fi", showBackground = true, widthDp = 380, heightDp = 940)
@Composable
private fun PreviewIdleEmpty() {
    PreviewHost(
        state = UiState(
            tvIp = "",
            localIp = null,
            uiError = "读取 APK 失败：文件无法访问",
        ),
    )
}

@Preview(name = "WAITING_CONFIRM", showBackground = true, widthDp = 380, heightDp = 960)
@Composable
private fun PreviewWaiting() {
    val apk = sampleApk()
    val now = System.currentTimeMillis()
    PreviewHost(
        state = UiState(
            push = PushState(
                phase = PushPhase.WAITING_CONFIRM,
                apk = apk,
                tvHost = "192.168.3.212",
                message = "等待电视确认安装请求",
                log = sampleLog(),
                startedAtMillis = now - 23_000L,
            ),
            tvIp = "192.168.3.212",
            apk = apk,
            localIp = "192.168.3.24",
        ),
    )
}

@Preview(name = "WAITING_CONFIRM · 深色", showBackground = true, widthDp = 380, heightDp = 960)
@Composable
private fun PreviewWaitingDark() {
    val apk = sampleApk()
    val now = System.currentTimeMillis()
    PreviewHost(
        darkTheme = true,
        state = UiState(
            push = PushState(
                phase = PushPhase.WAITING_CONFIRM,
                apk = apk,
                tvHost = "192.168.3.212",
                message = "等待电视确认安装请求",
                log = sampleLog(),
                startedAtMillis = now - 23_000L,
            ),
            tvIp = "192.168.3.212",
            apk = apk,
            localIp = "192.168.3.24",
        ),
    )
}

@Preview(name = "SUCCESS", showBackground = true, widthDp = 380, heightDp = 940)
@Composable
private fun PreviewSuccess() {
    val apk = sampleApk()
    val now = System.currentTimeMillis()
    PreviewHost(
        state = UiState(
            push = PushState(
                phase = PushPhase.SUCCESS,
                apk = apk,
                tvHost = "192.168.3.212",
                percent = 100,
                message = "安装完成",
                log = sampleLog(),
                startedAtMillis = now - 47_000L,
                finishedAtMillis = now - 3_000L,
            ),
            tvIp = "192.168.3.212",
            apk = apk,
            localIp = "192.168.3.24",
        ),
    )
}

@Preview(name = "FAILED", showBackground = true, widthDp = 380, heightDp = 940)
@Composable
private fun PreviewFailed() {
    val apk = sampleApk()
    val now = System.currentTimeMillis()
    PreviewHost(
        state = UiState(
            push = PushState(
                phase = PushPhase.FAILED,
                apk = apk,
                tvHost = "192.168.3.212",
                message = "推送失败",
                error = "连接电视失败：Connection refused (192.168.3.212:6553)",
                log = sampleLog(failed = true),
                startedAtMillis = now - 46_000L,
                finishedAtMillis = now - 2_000L,
            ),
            tvIp = "192.168.3.212",
            apk = apk,
            localIp = "192.168.3.24",
        ),
    )
}
