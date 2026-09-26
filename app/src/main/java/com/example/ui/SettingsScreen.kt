package com.example.ui

import android.content.pm.PackageInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.i18n.I18n
import com.example.i18n.tr
import com.example.settings.AppSettings
import com.example.ui.components.qClickable
import com.example.ui.theme.QboostBlue
import com.example.ui.theme.QboostBlueGlow
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import kotlin.math.roundToInt

enum class SettingsCategory {
    GENERAL,
    AUDIO,
    NOTIFICATIONS,
    PANEL,
    DISPLAY,
    ABOUT
}

/**
 * Qboost settings: categories on the left, the settings of the chosen category on the right
 * (same layout as the GameHub / PC Engine settings screen).
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    isMusicMuted: Boolean,
    isVibrationEnabled: Boolean,
    isClickSoundEnabled: Boolean,
    isControllerConnected: Boolean,
    notificationsEnabled: Boolean,
    updateState: UpdateUiState,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onControllerHintsChange: (Int) -> Unit,
    onToggleMusic: () -> Unit,
    onMusicVolumeChange: (Float) -> Unit,
    onToggleVibration: () -> Unit,
    onToggleClickSound: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onUpdateAlertsChange: (Boolean) -> Unit,
    onHandleOpacityChange: (Float) -> Unit,
    onPanelOpacityChange: (Float) -> Unit,
    onShowDockChange: (Boolean) -> Unit,
    onScalerSharpnessChange: (Float) -> Unit,
    onResetSaturation: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenGithub: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "?"

    var category by remember { mutableStateOf(SettingsCategory.GENERAL) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showHintsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0A1220), Color(0xFF0B1D33), Color(0xFF0E1830))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)
        ) {
            // ---------- Header: < Settings                              [ v8.0 ] ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .qClickable(onClick = onBack)
                        .testTag("settings_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = tr("settings"),
                        tint = TextWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tr("settings"),
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(QboostBlue)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "v$versionName",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // ---------- Left rail: categories ----------
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x66121C30))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    SettingsCategory.entries.forEach { item ->
                        RailItem(
                            label = when (item) {
                                SettingsCategory.GENERAL -> tr("set_general")
                                SettingsCategory.AUDIO -> tr("set_audio")
                                SettingsCategory.NOTIFICATIONS -> tr("set_notifications")
                                SettingsCategory.PANEL -> tr("set_panel")
                                SettingsCategory.DISPLAY -> tr("set_display")
                                SettingsCategory.ABOUT -> tr("set_about")
                            },
                            selected = item == category,
                            tag = "settings_category_${item.name.lowercase()}",
                            onClick = { category = item }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // ---------- Right panel: the settings ----------
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x66121C30))
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    when (category) {
                        SettingsCategory.GENERAL -> {
                            SettingRow(
                                title = tr("language"),
                                description = tr("language_desc"),
                                onClick = { showLanguageDialog = true },
                                testTag = "setting_language"
                            ) { ValueChevron(I18n.nativeName(settings.language)) }
                            RowDivider()
                            SettingRow(
                                title = tr("controller"),
                                description = null
                            ) {
                                ValueText(if (isControllerConnected) tr("connected") else tr("not_connected"))
                            }
                            RowDivider()
                            SettingRow(
                                title = tr("controller_hints"),
                                description = tr("controller_hints_desc"),
                                onClick = { showHintsDialog = true },
                                testTag = "setting_controller_hints"
                            ) { ValueChevron(hintsLabel(settings.controllerHints)) }
                        }

                        SettingsCategory.AUDIO -> {
                            SettingRow(
                                title = tr("lobby_music"),
                                description = tr("lobby_music_desc"),
                                onClick = onToggleMusic,
                                testTag = "setting_music"
                            ) { SettingSwitch(checked = !isMusicMuted) }
                            RowDivider()
                            SettingRow(title = tr("music_volume"), description = null) {
                                SettingSlider(
                                    value = settings.musicVolume,
                                    valueRange = 0f..1f,
                                    onValueChange = onMusicVolumeChange,
                                    tag = "setting_music_volume"
                                )
                            }
                            RowDivider()
                            SettingRow(
                                title = tr("haptics"),
                                description = tr("haptics_desc"),
                                onClick = onToggleVibration,
                                testTag = "setting_haptics"
                            ) { SettingSwitch(checked = isVibrationEnabled) }
                            RowDivider()
                            SettingRow(
                                title = tr("click_sound"),
                                description = tr("click_sound_desc"),
                                onClick = onToggleClickSound,
                                testTag = "setting_click_sound"
                            ) { SettingSwitch(checked = isClickSoundEnabled) }
                        }

                        SettingsCategory.NOTIFICATIONS -> {
                            SettingRow(
                                title = tr("push_notifications"),
                                description = tr("push_notifications_desc"),
                                onClick = onOpenNotificationSettings,
                                testTag = "setting_push_notifications"
                            ) { SettingSwitch(checked = notificationsEnabled) }
                            RowDivider()
                            SettingRow(
                                title = tr("update_alerts"),
                                description = tr("update_alerts_desc"),
                                onClick = { onUpdateAlertsChange(!settings.updateAlerts) },
                                testTag = "setting_update_alerts"
                            ) { SettingSwitch(checked = settings.updateAlerts) }
                        }

                        SettingsCategory.PANEL -> {
                            SettingRow(
                                title = tr("handle_opacity"),
                                description = tr("handle_opacity_desc")
                            ) {
                                SettingSlider(
                                    value = settings.handleOpacity,
                                    valueRange = 0f..1f,
                                    onValueChange = onHandleOpacityChange,
                                    tag = "setting_handle_opacity"
                                )
                            }
                            RowDivider()
                            SettingRow(
                                title = tr("panel_opacity"),
                                description = tr("panel_opacity_desc")
                            ) {
                                SettingSlider(
                                    value = settings.panelOpacity,
                                    valueRange = 0.3f..1f,
                                    onValueChange = onPanelOpacityChange,
                                    tag = "setting_panel_opacity"
                                )
                            }
                            RowDivider()
                            SettingRow(
                                title = tr("show_dock"),
                                description = tr("show_dock_desc"),
                                onClick = { onShowDockChange(!settings.showDock) },
                                testTag = "setting_dock"
                            ) { SettingSwitch(checked = settings.showDock) }
                        }

                        SettingsCategory.DISPLAY -> {
                            SettingRow(
                                title = tr("scaler_sharpness"),
                                description = tr("scaler_sharpness_desc")
                            ) {
                                SettingSlider(
                                    value = settings.scalerSharpness,
                                    valueRange = 0f..1f,
                                    onValueChange = onScalerSharpnessChange,
                                    tag = "setting_scaler_sharpness"
                                )
                            }
                            RowDivider()
                            SettingRow(
                                title = tr("reset_saturation"),
                                description = tr("reset_saturation_desc"),
                                onClick = onResetSaturation,
                                testTag = "setting_reset_saturation"
                            ) { ValueChevron("100%") }
                        }

                        SettingsCategory.ABOUT -> {
                            SettingRow(title = tr("version"), description = null) { ValueText(versionName) }
                            RowDivider()
                            SettingRow(title = tr("build"), description = null) {
                                ValueText("${packageInfo?.buildNumber() ?: 0L}")
                            }
                            RowDivider()
                            SettingRow(title = tr("package"), description = null) { ValueText(context.packageName) }
                            RowDivider()
                            SettingRow(title = tr("device"), description = null) {
                                ValueText("${Build.MANUFACTURER} ${Build.MODEL}")
                            }
                            RowDivider()
                            SettingRow(title = tr("android"), description = null) {
                                ValueText("${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                            }
                            RowDivider()
                            SettingRow(
                                title = tr("check_updates"),
                                description = when (updateState) {
                                    UpdateUiState.Idle -> tr("check_updates_desc")
                                    UpdateUiState.Checking -> tr("checking")
                                    UpdateUiState.UpToDate -> tr("up_to_date")
                                    is UpdateUiState.Available -> tr("update_available", updateState.latest)
                                    UpdateUiState.Failed -> tr("update_failed")
                                },
                                onClick = onCheckUpdates,
                                testTag = "setting_check_updates"
                            ) { ValueChevron("") }
                            RowDivider()
                            SettingRow(
                                title = tr("open_github"),
                                description = null,
                                onClick = onOpenGithub,
                                testTag = "setting_open_github"
                            ) { ValueChevron("") }
                        }
                    }
                }
            }
        }

        if (showLanguageDialog) {
            ChoiceDialog(
                title = tr("language"),
                options = I18n.languages.map { it.code to it.nativeName },
                selected = settings.language,
                onSelect = { onLanguageChange(it) },
                onDismiss = { showLanguageDialog = false }
            )
        }
        if (showHintsDialog) {
            ChoiceDialog(
                title = tr("controller_hints"),
                options = listOf(
                    "0" to tr("opt_auto"),
                    "1" to tr("opt_always"),
                    "2" to tr("opt_never")
                ),
                selected = settings.controllerHints.toString(),
                onSelect = { onControllerHintsChange(it.toIntOrNull() ?: 0) },
                onDismiss = { showHintsDialog = false }
            )
        }
    }
}

@Composable
private fun hintsLabel(mode: Int): String = when (mode) {
    AppSettings.CONTROLLER_HINTS_ALWAYS -> tr("opt_always")
    AppSettings.CONTROLLER_HINTS_NEVER -> tr("opt_never")
    else -> tr("opt_auto")
}

@Suppress("DEPRECATION")
private fun PackageInfo.buildNumber(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

@Composable
private fun RailItem(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x26FFFFFF) else Color.Transparent)
            .qClickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Color.White else Color.Transparent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = if (selected) TextWhite else TextGray,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextGray,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String?,
    onClick: (() -> Unit)? = null,
    testTag: String = "",
    trailing: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.qClickable(onClick = onClick) else Modifier)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(
                    text = description,
                    color = TextGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(Color(0x14FFFFFF))
    )
}

@Composable
private fun ValueText(text: String) {
    Text(
        text = text,
        color = TextGray,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 260.dp)
    )
}

@Composable
private fun ValueChevron(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (value.isNotEmpty()) {
            ValueText(value)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextGray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = QboostBlue,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color(0xFFB0B7C6),
            uncheckedTrackColor = Color(0xFF2A3348),
            uncheckedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun SettingSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = QboostBlue,
                inactiveTrackColor = Color(0x33FFFFFF)
            ),
            modifier = Modifier
                .width(170.dp)
                .testTag(tag)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(value * 100).roundToInt()}%",
            color = QboostBlueGlow,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF131C2E))
                .padding(vertical = 14.dp)
        ) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { (code, label) ->
                    val isSelected = code == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .qClickable {
                                onSelect(code)
                                onDismiss()
                            }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) TextWhite else TextGray,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) QboostBlue else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
