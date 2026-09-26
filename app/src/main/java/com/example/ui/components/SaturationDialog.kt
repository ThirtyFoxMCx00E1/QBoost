package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.display.SaturationEngine
import com.example.model.GameItem
import com.example.model.SaturationUiState
import com.example.ui.theme.QboostNeonCyan
import com.example.ui.theme.QboostBlue
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val SATURATION_PRESETS = listOf(
    "Normal" to 1.0f,
    "Vivid" to 1.3f,
    "Cyber" to 1.7f,
    "Max" to 2.0f
)

/**
 * Saturation settings for a game. The slider previews live on the whole screen (through
 * [SaturationEngine]); "Save" stores the value for this game so it is applied when the game starts.
 */
@Composable
fun SaturationDialog(
    game: GameItem,
    state: SaturationUiState,
    onApply: (Float) -> Unit,
    onSave: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val startValue = remember { if (state.attempted) state.appliedValue else SaturationEngine.NEUTRAL }
    var value by remember { mutableFloatStateOf(game.saturation.coerceIn(SaturationEngine.MIN, SaturationEngine.MAX)) }
    var touched by remember { mutableStateOf(false) }
    // Bumped by preset / reset buttons so they apply even when the value does not change.
    var applyTick by remember { mutableIntStateOf(0) }

    // Live preview, debounced so dragging the slider does not spam the system.
    LaunchedEffect(value, applyTick) {
        if (touched) {
            delay(180)
            onApply(value)
        }
    }

    // Closing without saving puts the screen back the way it was.
    fun finish(save: Boolean) {
        if (save) onSave(value)
        if (touched) onApply(startValue)
        onDismiss()
    }

    Dialog(
        onDismissRequest = { finish(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF13161F))
                .padding(16.dp)
                .testTag("saturation_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = null,
                            tint = QboostNeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${game.name} · Saturation",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = clickSound { finish(false) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Screen saturation (0% = grayscale, 100% = normal)", color = TextGray, fontSize = 11.sp)
                    Text(
                        text = "${(value * 100).roundToInt()}%",
                        color = QboostNeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = value,
                    onValueChange = {
                        value = (it * 20).roundToInt() / 20f
                        touched = true
                    },
                    valueRange = SaturationEngine.MIN..SaturationEngine.MAX,
                    colors = SliderDefaults.colors(
                        thumbColor = QboostNeonCyan,
                        activeTrackColor = QboostNeonCyan,
                        inactiveTrackColor = Color(0xFF272F3E)
                    ),
                    modifier = Modifier.testTag("saturation_slider_lobby")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SATURATION_PRESETS.forEach { (label, preset) ->
                        FilterChip(
                            selected = abs(value - preset) < 0.01f,
                            onClick = clickSound {
                                value = preset
                                touched = true
                                applyTick++
                            },
                            label = { Text("$label ${(preset * 100).roundToInt()}%", fontSize = 11.sp) },
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = QboostBlue,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E2433),
                                labelColor = TextGray
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                SaturationStatusCard(state = state, value = value)

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = clickSound { SaturationEngine.copyAdbCommand(context, value) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2437)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy ADB command", color = QboostNeonCyan, fontSize = 11.sp)
                    }
                    Button(
                        onClick = clickSound {
                            value = SaturationEngine.NEUTRAL
                            touched = true
                            applyTick++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2437)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset 100%", color = TextWhite, fontSize = 11.sp)
                    }
                    Button(
                        onClick = clickSound { finish(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = QboostBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_saturation_button")
                    ) {
                        Text("Save for game", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SaturationStatusCard(state: SaturationUiState, value: Float) {
    val accent = when {
        !state.attempted -> Color(0xFF2E364A)
        state.ok -> Color(0xFF00E676)
        else -> Color(0xFFFFB74D)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F1219))
            .padding(10.dp)
    ) {
        Column {
            when {
                !state.attempted -> Text(
                    text = "Move the slider to preview it on your whole screen. This uses Android's SurfaceFlinger " +
                        "saturation control, which needs root (or the ADB command below, run once).",
                    color = TextGray,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                state.ok -> Text(
                    text = "✅ ${state.message}",
                    color = Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                else -> {
                    Text(
                        text = "⚠ ${state.message}",
                        color = Color(0xFFFFB74D),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SaturationEngine.adbCommand(value),
                        color = TextWhite,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
