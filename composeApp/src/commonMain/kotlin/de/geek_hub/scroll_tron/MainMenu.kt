package de.geek_hub.scroll_tron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import scrolltron.composeapp.generated.resources.Res
import scrolltron.composeapp.generated.resources.orbitron_bold
import scrolltron.composeapp.generated.resources.orbitron_regular
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Neon palette (shared with game)
// ---------------------------------------------------------------------------

private val NEON_CYAN   = Color(0xFF00FFFF)
private val NEON_PINK   = Color(0xFFFF00FF)
private val GRID_COLOR  = Color(0xFF0D2A0D)
private val BG_COLOR    = Color(0xFF020C02)

// ---------------------------------------------------------------------------
// Main Menu
// ---------------------------------------------------------------------------

@Composable
fun MainMenu(
    onSingleplayer: () -> Unit,
    onMultiplayer: () -> Unit,
) {
    val gameFont = FontFamily(
        Font(Res.font.orbitron_regular, FontWeight.Normal),
        Font(Res.font.orbitron_bold, FontWeight.Bold),
    )

    val multiplayerSupported = isMultiplayerSupported()

    // Animate a pulse for the glow effect
    var frameCount by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                frameCount += 0.02f
            }
        }
    }
    val glowAlpha = 0.5f + 0.5f * sin(frameCount)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Animated grid background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(BG_COLOR)

            // Grid
            val step = 60f
            var x = 0f
            while (x <= size.width) {
                drawLine(GRID_COLOR, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(GRID_COLOR, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }

            // Subtle horizontal scan line effect
            val scanY = ((frameCount * 80f) % (size.height + 200f)) - 100f
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, NEON_CYAN.copy(alpha = 0.08f), Color.Transparent),
                ),
                start = Offset(0f, scanY),
                end = Offset(size.width, scanY),
                strokeWidth = 60f,
            )
        }

        // Menu content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Title
            Text(
                text = "SCROLL",
                style = TextStyle(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = gameFont,
                    color = NEON_CYAN,
                    shadow = Shadow(
                        color = NEON_CYAN.copy(alpha = glowAlpha * 0.6f),
                        offset = Offset.Zero,
                        blurRadius = 30f,
                    ),
                ),
            )
            Text(
                text = "TRON",
                style = TextStyle(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = gameFont,
                    color = NEON_PINK,
                    shadow = Shadow(
                        color = NEON_PINK.copy(alpha = glowAlpha * 0.6f),
                        offset = Offset.Zero,
                        blurRadius = 30f,
                    ),
                ),
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Singleplayer button
            MenuButton(
                text = "SINGLEPLAYER",
                color = NEON_CYAN,
                gameFont = gameFont,
                onClick = onSingleplayer,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Multiplayer button
            if (multiplayerSupported) {
                MenuButton(
                    text = "MULTIPLAYER",
                    color = NEON_PINK,
                    gameFont = gameFont,
                    onClick = onMultiplayer,
                )
            } else {
                // Disabled multiplayer on non-web platforms
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .border(
                            width = 1.dp,
                            color = Color(0xFF333333),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 36.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "MULTIPLAYER",
                            fontFamily = gameFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF444444),
                        )
                        Text(
                            text = "WEB ONLY",
                            fontFamily = gameFont,
                            fontSize = 10.sp,
                            color = Color(0xFF333333),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    color: Color,
    gameFont: FontFamily,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 36.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = gameFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color,
        )
    }
}
