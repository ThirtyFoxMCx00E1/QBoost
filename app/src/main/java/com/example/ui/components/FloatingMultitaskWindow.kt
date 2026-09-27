package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.QboostNeonCyan
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import kotlin.math.roundToInt

@Composable
fun FloatingMultitaskWindow(
    appType: String, // "youtube", "browser", "notes", "clock", "phone"
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(60f) }
    var offsetY by remember { mutableFloatStateOf(60f) }
    var isPlayingVideo by remember { mutableStateOf(true) }
    var videoProgress by remember { mutableFloatStateOf(0.42f) }
    var noteText by remember { mutableStateOf("Boss Strategy: Dodge red aura attacks, use lightning elemental counter!") }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(width = 300.dp, height = 210.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF010131B))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(Color(0xFF191D28))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, 600f)
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, 400f)
                        }
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (appType) {
                                    "youtube" -> Color(0xFFE50914)
                                    "browser" -> Color(0xFF2979FF)
                                    else -> QboostNeonCyan
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (appType) {
                            "youtube" -> "YouTube Floating Player"
                            "browser" -> "Gaming Wiki & Guides"
                            "notes" -> "Quick Strategy Notes"
                            "clock" -> "Stopwatch & Timer"
                            else -> "Floating Companion"
                        },
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(onClick = clickSound(onClose), modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Floating Window",
                            tint = TextGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Window Content based on App Type
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                when (appType) {
                    "youtube" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Simulated video display
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF202636), Color(0xFF0F1218))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = clickSound { isPlayingVideo = !isPlayingVideo },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingVideo) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Text(
                                        text = "Wuthering Waves: Ultimate 120FPS Guide",
                                        color = TextWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { videoProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = Color(0xFFE50914),
                                trackColor = Color(0xFF333A4A)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("04:28 / 10:15", color = TextGray, fontSize = 9.sp)
                                Text("1080p60 HDR", color = QboostNeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    "browser" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E2432))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "https://wiki.gaminghub.gg/builds/tier-list",
                                    color = QboostNeonCyan,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF161922))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "✦ S-Tier Build: Crit Rate +75%, Energy Recharge +120%\n✦ Best Echo Set: 5-piece Molten Rift\n✦ Rotation: Skill -> Echo -> Liberation -> Heavy Attack",
                                    color = TextWhite,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                    "notes" -> {
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            modifier = Modifier.fillMaxSize(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = QboostNeonCyan,
                                unfocusedBorderColor = Color(0xFF313848)
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Qboost Gaming Companion", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Background Optimization Active", color = QboostNeonCyan, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
