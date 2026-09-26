package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Neutral dark grey, not the app's usual blue-black [com.example.ui.theme.CyberDarkBg] — this is the
 *  color the launch window itself is set to (see Theme.MyApplication's android:windowBackground), so the
 *  very first frame the system draws, this splash, and the games menu it fades into all read as one
 *  continuous grey, with no color flash at any handoff. */
val SplashGrey = Color(0xFF1C1C1E)

/**
 * The app's animated launch logo: a quick spring "pop" (scale + spin) on the rocket mark, settling back
 * to rest, then the QBOOST wordmark slides in right beside it — logo and text are kept close together on
 * purpose, as one tight lockup rather than two separate elements. The whole thing holds on screen for 9
 * seconds total, then [onFinished] fires so the caller can cross-fade into the game space menu.
 *
 * The rocket mark is [R.drawable.qboost_splash_glyph], a transparent-background PNG (no filled icon
 * backdrop) — the same drawable the OS's own cold-start frame shows via `windowSplashScreenAnimatedIcon`
 * in themes.xml, so that frame and this one read as one continuous splash instead of two different-looking
 * screens in a row.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffset = remember { Animatable(56f) }

    LaunchedEffect(Unit) {
        val pop = spring<Float>(dampingRatio = 0.55f, stiffness = 380f)

        // Pop: scale up and spin to -135°...
        launch { scale.animateTo(1.35f, pop) }
        rotation.animateTo(-135f, pop)
        // ...then settle straight back to resting size/rotation.
        launch { scale.animateTo(1f, pop) }
        rotation.animateTo(0f, pop)

        // Reveal: the wordmark slides in and fades in beside the now-settled logo.
        val reveal = tween<Float>(durationMillis = 1550, easing = FastOutSlowInEasing)
        launch { textOffset.animateTo(0f, reveal) }
        textAlpha.animateTo(1f, reveal)

        // Hold the finished lockup on screen so the whole splash lasts 9s, then hand off.
        delay(9_000L - 800L - 1_550L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashGrey),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.qboost_splash_glyph),
                contentDescription = null,
                modifier = Modifier
                    .size(92.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        rotationZ = rotation.value
                    }
            )
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    translationX = textOffset.value
                }
            ) {
                Text(
                    text = "QBOOST",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
