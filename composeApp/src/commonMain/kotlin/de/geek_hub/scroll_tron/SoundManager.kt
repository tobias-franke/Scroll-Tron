package de.geek_hub.scroll_tron

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
enum class SoundEffect {
    GAME_START,
    CRASH,
}

expect object PlatformAudio {
    fun play(sound: SoundEffect)
}

object SoundManager {
    var isMuted: Boolean by mutableStateOf(false)

    fun toggleMute() {
        isMuted = !isMuted
    }

    fun play(sound: SoundEffect) {
        if (isMuted) return
        try {
            PlatformAudio.play(sound)
        } catch (_: Throwable) {}
    }

    fun playStart() = play(SoundEffect.GAME_START)
    fun playCrash() = play(SoundEffect.CRASH)
}

@Composable
fun SoundToggleButton(
    modifier: Modifier = Modifier,
    scaleFactor: Float = 1f,
    gameFont: FontFamily? = null,
) {
    val isMuted = SoundManager.isMuted
    val buttonColor = if (isMuted) Color(0xFF666666) else Color(0xFF00FFFF)
    val label = if (isMuted) "SOUND: OFF" else "SOUND: ON"

    Box(
        modifier = modifier
            .background(
                color = Color(0xEE0A0E14),
                shape = RoundedCornerShape((4 / scaleFactor).dp),
            )
            .border(
                width = (1 / scaleFactor).dp,
                color = buttonColor.copy(alpha = 0.7f),
                shape = RoundedCornerShape((4 / scaleFactor).dp),
            )
            .clickable { SoundManager.toggleMute() }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = (14 / scaleFactor).dp, vertical = (8 / scaleFactor).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = gameFont,
            fontWeight = FontWeight.Bold,
            fontSize = (12 / scaleFactor).sp,
            color = buttonColor,
        )
    }
}
