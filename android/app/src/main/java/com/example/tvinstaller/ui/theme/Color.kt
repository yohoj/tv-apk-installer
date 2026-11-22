package com.example.tvinstaller.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Calm instrument-panel palette.
 * Chalk paper + ink + one pine/teal accent; a single warm amber is reserved for
 * "the TV is waiting on you", and a clay red for failures. Steel blue is used
 * only inside the diagnostic log for TV-side chatter.
 */
internal object LightInk {
    val background = Color(0xFFF6F5F0)
    val surface = Color(0xFFFFFFFF)
    val surfaceAlt = Color(0xFFEFEEE7)
    val ink = Color(0xFF131917)
    val inkMuted = Color(0xFF5E6863)

    val primary = Color(0xFF0B6B57)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFCDEDE0)
    val onPrimaryContainer = Color(0xFF00372A)

    val secondary = Color(0xFF40534C)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFE1E8E2)
    val onSecondaryContainer = Color(0xFF111C17)

    val waiting = Color(0xFFA85B00)
    val waitingContainer = Color(0xFFFFEBCB)
    val onWaitingContainer = Color(0xFF3B2200)

    val danger = Color(0xFFA3312B)
    val dangerContainer = Color(0xFFFADBD7)
    val onDangerContainer = Color(0xFF3C0F0C)

    val info = Color(0xFF2E5F80)
    val hairline = Color(0xFFDCDAD0)
    val outline = Color(0xFFB9BDB4)
    val track = Color(0xFFE7E6DE)
    val inverseSurface = Color(0xFF1D2623)
    val inverseOnSurface = Color(0xFFF1F5F1)
    val scrim = Color(0xFF000000)
}

internal object DarkInk {
    val background = Color(0xFF0B100F)
    val surface = Color(0xFF131A18)
    val surfaceAlt = Color(0xFF1A2320)
    val ink = Color(0xFFE9EFEA)
    val inkMuted = Color(0xFF97A49E)

    val primary = Color(0xFF6FE0BE)
    val onPrimary = Color(0xFF00382B)
    val primaryContainer = Color(0xFF0C4A3B)
    val onPrimaryContainer = Color(0xFFBDF4E1)

    val secondary = Color(0xFFA9C2B6)
    val onSecondary = Color(0xFF16221C)
    val secondaryContainer = Color(0xFF243129)
    val onSecondaryContainer = Color(0xFFD6E6DC)

    val waiting = Color(0xFFFFB65C)
    val waitingContainer = Color(0xFF3B2A0B)
    val onWaitingContainer = Color(0xFFFFE3BB)

    val danger = Color(0xFFFF8E85)
    val dangerContainer = Color(0xFF3F1B18)
    val onDangerContainer = Color(0xFFFFD9D5)

    val info = Color(0xFF8CBBDD)
    val hairline = Color(0xFF27322E)
    val outline = Color(0xFF3A4741)
    val track = Color(0xFF222C29)
    val inverseSurface = Color(0xFFE1E9E3)
    val inverseOnSurface = Color(0xFF232C29)
    val scrim = Color(0xFF000000)
}
