package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PlayfairDisplayFamily
import com.example.ui.theme.SpaceGroteskFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IntroSplashScreen(onFinished: () -> Unit) {
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetDp = remember { Animatable(18f) }
    val lineProgress = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }
    var dismissed by remember { mutableStateOf(false) }

    val finishNow: () -> Unit = {
        if (!dismissed) {
            dismissed = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        launch {
            titleAlpha.animateTo(1f, animationSpec = tween(750, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetDp.animateTo(0f, animationSpec = tween(850, easing = FastOutSlowInEasing))
        }
        delay(280L)
        launch {
            lineProgress.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        }
        delay(260L)
        subtitleAlpha.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        delay(1350L)
        if (!dismissed) {
            screenAlpha.animateTo(0f, animationSpec = tween(420, easing = FastOutSlowInEasing))
            finishNow()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha.value)
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = finishNow
            )
            .safeDrawingPadding()
            .padding(horizontal = 32.dp)
            .testTag("intro_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Subtle radial champagne glow on pure black canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x28D4AF37),
                        Color(0x0EE5C76B),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.48f),
                    radius = size.minDimension * 0.62f
                )
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Subtle diamond emblem above title
            Box(
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .padding(bottom = 18.dp)
                    .size(10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = size.minDimension / 2f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy - r)
                        lineTo(cx + r, cy)
                        lineTo(cx, cy + r)
                        lineTo(cx - r, cy)
                        close()
                    }
                    drawPath(path, color = Color(0xFFD4AF37))
                }
            }

            Text(
                text = stringResource(R.string.intro_credit_title),
                fontFamily = PlayfairDisplayFamily,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 31.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.4.sp,
                color = Color(0xFFF7F2E7),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = titleOffsetDp.value.dp)
                    .alpha(titleAlpha.value)
                    .testTag("intro_creator_text")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Expanding champagne-gold hairline divider
            Box(
                modifier = Modifier
                    .width((150f * lineProgress.value).dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFD4AF37),
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.intro_credit_sub),
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 2.6.sp,
                color = Color(0xFF9EA6A0),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(subtitleAlpha.value)
                    .testTag("intro_by_ai_text")
            )
        }
    }
}
