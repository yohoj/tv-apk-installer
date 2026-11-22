package com.example.tvinstaller.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tvinstaller.core.LogLevel
import com.example.tvinstaller.core.LogLine
import com.example.tvinstaller.core.PushPhase
import com.example.tvinstaller.ui.theme.MonoLabel
import com.example.tvinstaller.ui.theme.MonoLog
import com.example.tvinstaller.ui.theme.MonoNumber
import com.example.tvinstaller.ui.theme.tv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------------------------------------------------------------------------
 * Formatting helpers
 * ------------------------------------------------------------------------- */

internal fun formatElapsed(millis: Long): String {
    val total = (millis / 1000L).coerceAtLeast(0L)
    val minutes = total / 60L
    val seconds = total % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

internal fun formatApkSize(bytes: Long): String {
    if (bytes <= 0L) return "大小未知"
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) {
        String.format(Locale.US, "%.1f MB", megabytes)
    } else {
        String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

internal fun phaseStatusLabel(phase: PushPhase): String = when (phase) {
    PushPhase.IDLE -> "待机"
    PushPhase.PREPARING -> "准备中"
    PushPhase.CONNECTING -> "连接中"
    PushPhase.WAITING_CONFIRM -> "待确认"
    PushPhase.DOWNLOADING -> "下载中"
    PushPhase.INSTALLING -> "安装中"
    PushPhase.SUCCESS -> "已完成"
    PushPhase.FAILED -> "未完成"
}

/** Index into 准备 → 连接 → 确认 → 下载 → 安装 → 完成, or -1 when nothing ran. */
internal fun phaseStepIndex(phase: PushPhase): Int = when (phase) {
    PushPhase.PREPARING -> 0
    PushPhase.CONNECTING -> 1
    PushPhase.WAITING_CONFIRM -> 2
    PushPhase.DOWNLOADING -> 3
    PushPhase.INSTALLING -> 4
    PushPhase.SUCCESS -> 5
    PushPhase.IDLE, PushPhase.FAILED -> -1
}

internal fun phaseFallbackMessage(phase: PushPhase): String = when (phase) {
    PushPhase.IDLE -> ""
    PushPhase.PREPARING -> "正在准备安装包…"
    PushPhase.CONNECTING -> "正在连接电视…"
    PushPhase.WAITING_CONFIRM -> "电视正在等待确认"
    PushPhase.DOWNLOADING -> "正在把安装包发送到电视…"
    PushPhase.INSTALLING -> "电视正在安装，请稍候…"
    PushPhase.SUCCESS -> "安装完成"
    PushPhase.FAILED -> "推送没有完成"
}

/* ---------------------------------------------------------------------------
 * Building blocks
 * ------------------------------------------------------------------------- */

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.tv.hairline,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = color,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
internal fun SectionHeader(
    index: String,
    title: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index,
            style = MonoLabel,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .width(14.dp)
                .height(1.dp)
                .background(MaterialTheme.tv.hairline),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun Pill(
    text: String,
    color: Color,
    container: Color,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    dotPulse: Float = 1f,
    dotColor: Color = color,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(
                Modifier
                    .size(6.dp)
                    .alpha(dotPulse)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MonoLabel,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SignalBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = MaterialTheme.tv.track,
    fill: Color = MaterialTheme.colorScheme.primary,
) {
    val target = (progress ?: 0f).coerceIn(0f, 1f)
    val filled by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "signalFill",
    )
    val transition = rememberInfiniteTransition(label = "signalSweep")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "signalSweepOffset",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track),
    ) {
        val h = size.height
        val w = size.width
        val radius = CornerRadius(h / 2f, h / 2f)
        if (progress == null) {
            val segment = w * 0.32f
            val x = -segment + (w + segment) * sweep
            drawRoundRect(
                color = fill.copy(alpha = 0.85f),
                topLeft = Offset(x, 0f),
                size = Size(segment, h),
                cornerRadius = radius,
            )
        } else if (filled > 0.002f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset.Zero,
                size = Size(w * filled, h),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
internal fun PhaseStepper(
    activeIndex: Int,
    tone: Color,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
) {
    val steps = remember { listOf("准备", "连接", "确认", "下载", "安装", "完成") }
    val inactive = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEachIndexed { index, label ->
            val reached = !failed && activeIndex >= 0 && index <= activeIndex
            val isActive = !failed && index == activeIndex
            val barColor by animateColorAsState(
                targetValue = when {
                    failed -> MaterialTheme.tv.track
                    reached && isActive -> tone
                    reached -> tone.copy(alpha = 0.5f)
                    else -> MaterialTheme.tv.track
                },
                animationSpec = tween(durationMillis = 380),
                label = "stepBar$index",
            )
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(barColor),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (reached) {
                        inactive
                    } else {
                        inactive.copy(alpha = 0.42f)
                    },
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * The critical moment: the TV is asking the user to press 同意
 * ------------------------------------------------------------------------- */

@Composable
internal fun WaitingCard(
    host: String,
    elapsedMillis: Long,
    modifier: Modifier = Modifier,
) {
    val palette = MaterialTheme.tv
    val transition = rememberInfiniteTransition(label = "waitPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waitPulseAlpha",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = palette.waitingContainer,
        border = BorderStroke(1.dp, palette.waiting.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(
                    text = "等待电视确认",
                    color = palette.onWaitingContainer,
                    container = palette.waiting.copy(alpha = 0.16f),
                    dot = true,
                    dotPulse = pulse,
                    dotColor = palette.waiting,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "已等待 ${formatElapsed(elapsedMillis)}",
                    style = MonoLabel,
                    color = palette.onWaitingContainer.copy(alpha = 0.72f),
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteKeyBadge(pulse = pulse)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "请在电视上按遥控器确认",
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.onWaitingContainer,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (host.isBlank()) "目标电视：未知" else "目标电视 $host",
                        style = MonoLabel,
                        color = palette.onWaitingContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            ConfirmStep(
                step = 1,
                title = "按遥控器「右」",
                detail = "电视屏幕弹出多屏互动提示后，先按右键",
            )
            Spacer(Modifier.height(10.dp))
            ConfirmStep(
                step = 2,
                title = "选择「同意」",
                detail = "用方向键把光标移到「同意」上",
            )
            Spacer(Modifier.height(10.dp))
            ConfirmStep(
                step = 3,
                title = "按「确认 / OK」",
                detail = "电视会自动下载并安装，手机会继续等待",
            )

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.waiting.copy(alpha = 0.25f)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "这一步需要你在电视上手动操作，手机端无法代按，完成后会自动继续。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onWaitingContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun ConfirmStep(step: Int, title: String, detail: String) {
    val palette = MaterialTheme.tv
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(palette.waiting),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step.toString(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = palette.waitingContainer,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onWaitingContainer,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = palette.onWaitingContainer.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun RemoteKeyBadge(pulse: Float) {
    val palette = MaterialTheme.tv
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(52.dp)
                .alpha(pulse * 0.35f)
                .clip(CircleShape)
                .background(palette.waiting),
        )
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(palette.waiting),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(13.dp)) {
                val arrow = Path().apply {
                    moveTo(size.width * 0.14f, size.height * 0.06f)
                    lineTo(size.width * 0.94f, size.height * 0.5f)
                    lineTo(size.width * 0.14f, size.height * 0.94f)
                    close()
                }
                drawPath(arrow, color = palette.waitingContainer)
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * Ordinary running phases
 * ------------------------------------------------------------------------- */

@Composable
internal fun ProgressCard(
    phase: PushPhase,
    message: String,
    percent: Int?,
    elapsedMillis: Long,
    downloadUrl: String?,
    modifier: Modifier = Modifier,
) {
    Panel(
        modifier = modifier,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = phaseStatusLabel(phase),
                color = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                dot = true,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "已用时 ${formatElapsed(elapsedMillis)}",
                style = MonoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = message.ifBlank { phaseFallbackMessage(phase) },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(14.dp))

        PhaseStepper(
            activeIndex = phaseStepIndex(phase),
            tone = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        if (percent != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = percent.toString(),
                    style = MonoNumber,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "%",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (phase == PushPhase.INSTALLING) "电视正在安装" else "电视正在接收",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        SignalBar(progress = percent?.let { it / 100f })

        if (!downloadUrl.isNullOrBlank() && phase == PushPhase.DOWNLOADING) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = downloadUrl,
                style = MonoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "请保持手机与电视在同一 Wi-Fi，不要锁屏退出。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ---------------------------------------------------------------------------
 * Success / failure
 * ------------------------------------------------------------------------- */

@Composable
internal fun SuccessCard(
    apkLabel: String,
    host: String,
    elapsedMillis: Long,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val label = apkLabel.ifBlank { "安装包" }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.primaryContainer,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(scheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "安装完成",
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onPrimaryContainer,
                    )
                    Text(
                        text = if (host.isBlank()) "已推送到电视" else "已推送到 $host",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Pill(
                    text = "用时 ${formatElapsed(elapsedMillis)}",
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    container = scheme.primary.copy(alpha = 0.14f),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "「$label」已在电视上装好，可以直接打开使用了。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
            )

            Spacer(Modifier.height(16.dp))

            PhaseStepper(activeIndex = 5, tone = scheme.primary)
        }
    }
}

@Composable
internal fun ErrorCard(
    error: String?,
    apkLabel: String?,
    modifier: Modifier = Modifier,
) {
    val palette = MaterialTheme.tv

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = palette.dangerContainer,
        border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = palette.danger,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "推送没有完成",
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.onDangerContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error?.takeIf { it.isNotBlank() }
                            ?: "电视没有响应，请检查网络和 IP 后重试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onDangerContainer.copy(alpha = 0.86f),
                    )
                }
            }

            if (!apkLabel.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "待推送：$apkLabel",
                    style = MonoLabel,
                    color = palette.onDangerContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = palette.danger.copy(alpha = 0.8f),
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "确认电视已开机、IP 填写正确、手机和电视连的是同一个 Wi-Fi，然后点下面的「重试推送」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onDangerContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * Diagnostic log
 * ------------------------------------------------------------------------- */

@Composable
internal fun LogPanel(
    lines: List<LogLine>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val visible = remember(lines) { if (lines.size > 300) lines.takeLast(300) else lines }
    val stamps = remember(visible) { visible.map { formatter.format(Date(it.timeMillis)) } }

    LaunchedEffect(visible.size, expanded) {
        if (expanded && visible.isNotEmpty()) {
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Panel(
        modifier = modifier,
        color = MaterialTheme.tv.surfaceAlt.copy(alpha = 0.55f),
        borderColor = MaterialTheme.tv.hairline,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "日志",
                style = MonoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = lines.size.toString(),
                style = MonoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起日志" else "展开日志",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
        ) {
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.tv.hairline),
                )
                Spacer(Modifier.height(8.dp))
                if (visible.isEmpty()) {
                    Text(
                        text = "暂无日志",
                        style = MonoLog,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(scroll),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            visible.forEachIndexed { index, line ->
                                LogRow(stamp = stamps.getOrElse(index) { "" }, line = line)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(stamp: String, line: LogLine) {
    val tone = logTone(line.level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stamp,
            style = MonoLog,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.width(56.dp),
        )
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(tone),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = line.text,
            style = MonoLog,
            color = tone,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun logTone(level: LogLevel): Color {
    val palette = MaterialTheme.tv
    return when (level) {
        LogLevel.INFO -> palette.logInfo
        LogLevel.STEP -> palette.logStep
        LogLevel.TV -> palette.logTv
        LogLevel.SUCCESS -> palette.logSuccess
        LogLevel.WARN -> palette.logWarn
        LogLevel.ERROR -> palette.logError
    }
}
