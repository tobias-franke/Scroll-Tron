package de.geek_hub.scroll_tron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
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
// Main Menu
// ---------------------------------------------------------------------------

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainMenu(
    onSingleplayer: () -> Unit,
    onMultiplayer: () -> Unit,
    onExit: () -> Unit = {},
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

    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onExit()
                    true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        // Atmospheric Cyber Grid & Scanline Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCyberGrid(size.width, size.height, step = 60f)
            drawCornerBrackets(size.width, size.height, CyberColors.NEON_CYAN.copy(alpha = 0.5f), bracketLength = 36f, bracketStroke = 2.5f, margin = 12f)

            // Animated horizontal scan beam
            val scanY = ((frameCount * 75f) % (size.height + 250f)) - 125f
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, CyberColors.NEON_CYAN.copy(alpha = 0.12f), Color.Transparent),
                ),
                start = Offset(0f, scanY),
                end = Offset(size.width, scanY),
                strokeWidth = 70f,
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
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = gameFont,
                    color = CyberColors.NEON_CYAN,
                    letterSpacing = 4.sp,
                    shadow = Shadow(
                        color = CyberColors.NEON_CYAN.copy(alpha = glowAlpha * 0.7f),
                        offset = Offset.Zero,
                        blurRadius = 36f,
                    ),
                ),
            )
            Text(
                text = "TRON",
                style = TextStyle(
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = gameFont,
                    color = CyberColors.NEON_PINK,
                    letterSpacing = 6.sp,
                    shadow = Shadow(
                        color = CyberColors.NEON_PINK.copy(alpha = glowAlpha * 0.7f),
                        offset = Offset.Zero,
                        blurRadius = 36f,
                    ),
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle Tagline with cyber accent
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.width(32.dp).height(1.dp).background(CyberColors.NEON_CYAN.copy(alpha = 0.5f)))
                Text(
                    text = "LIGHT CYCLE ARENA",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = gameFont,
                        color = CyberColors.DIM_TEXT,
                        letterSpacing = 4.sp,
                    ),
                )
                Box(modifier = Modifier.width(32.dp).height(1.dp).background(CyberColors.NEON_PINK.copy(alpha = 0.5f)))
            }

            Spacer(modifier = Modifier.height(56.dp))

            // Singleplayer button
            MenuButton(
                text = "SINGLEPLAYER",
                subtitle = "SOLO SURVIVAL",
                color = CyberColors.NEON_CYAN,
                gameFont = gameFont,
                onClick = onSingleplayer,
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Multiplayer button
            if (multiplayerSupported) {
                MenuButton(
                    text = "MULTIPLAYER",
                    subtitle = "UP TO 4 PLAYERS P2P",
                    color = CyberColors.NEON_PINK,
                    gameFont = gameFont,
                    onClick = onMultiplayer,
                )
            } else {
                // Disabled multiplayer on non-web platforms
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .background(Color(0x44000000), RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF1B3322),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "MULTIPLAYER",
                            fontFamily = gameFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF445548),
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "WEB BROWSER ONLY",
                            fontFamily = gameFont,
                            fontSize = 10.sp,
                            color = Color(0xFF334438),
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
        }

        // Bottom footer hint
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = "STEER WITH MOUSE WHEEL • ESC TO QUIT",
                fontFamily = gameFont,
                fontSize = 11.sp,
                color = CyberColors.DIM_TEXT.copy(alpha = 0.6f),
                letterSpacing = 1.sp,
            )
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuButton(
    text: String,
    subtitle: String? = null,
    color: Color,
    gameFont: FontFamily,
    onClick: () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }

    val bgColor = if (isHovered) {
        color.copy(alpha = 0.16f)
    } else {
        CyberColors.PANEL_BG
    }

    val borderColor = if (isHovered) {
        color
    } else {
        color.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .width(300.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(
                width = if (isHovered) 2.dp else 1.2.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 32.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                fontFamily = gameFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isHovered) Color.White else color,
                style = TextStyle(
                    shadow = if (isHovered) Shadow(color = color, blurRadius = 16f) else null,
                ),
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontFamily = gameFont,
                    fontSize = 10.sp,
                    color = if (isHovered) color else color.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
