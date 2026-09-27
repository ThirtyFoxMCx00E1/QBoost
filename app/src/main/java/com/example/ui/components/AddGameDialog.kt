package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.InstalledAppItem
import com.example.ui.theme.QboostNeonCyan
import com.example.ui.theme.QboostBlue
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite

/**
 * Add-game dialog.
 *
 * In landscape (Qboost is now locked to landscape) the dialog is split in two panes: the app list
 * on the left and the game name + "Add to Game Space" button on the right, so the button is
 * ALWAYS visible no matter how many apps are in the list. Portrait keeps the original stacked layout.
 */
@Composable
fun AddGameDialog(
    installedApps: List<InstalledAppItem>,
    onDismiss: () -> Unit,
    onAddGame: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var customGameName by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledAppItem?>(null) }

    // Only real games — never Play Store, YouTube, or any other non-game app on the device.
    val filteredApps = remember(installedApps, searchQuery) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            app.isGame && matchesSearch
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val confirmAdd: () -> Unit = {
        val finalName = customGameName.trim().ifEmpty {
            selectedApp?.name ?: "New Game"
        }
        val pkg = selectedApp?.packageName ?: ""
        onAddGame(finalName, pkg)
        onDismiss()
    }
    val canAdd = customGameName.isNotBlank() || selectedApp != null
    val onPickApp: (InstalledAppItem) -> Unit = { app ->
        selectedApp = app
        customGameName = app.name
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val containerModifier = if (isLandscape) {
            Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
        } else {
            Modifier.fillMaxWidth(0.94f)
        }

        Box(
            modifier = containerModifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF13161F))
                .padding(if (isLandscape) 14.dp else 20.dp)
                .testTag("add_game_dialog")
        ) {
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // ---------- LEFT: search + filters + app list ----------
                    Column(
                        modifier = Modifier
                            .weight(1.45f)
                            .fillMaxHeight()
                    ) {
                        AddGameHeader(onDismiss)
                        Spacer(modifier = Modifier.height(8.dp))
                        AddGameSearchField(searchQuery) { searchQuery = it }
                        Spacer(modifier = Modifier.height(8.dp))
                        AddGameAppList(
                            filteredApps = filteredApps,
                            selectedApp = selectedApp,
                            onPick = onPickApp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // ---------- RIGHT: chosen app, name, and the always-visible Add button ----------
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Selected app",
                                color = TextGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val chosen = selectedApp
                            Text(
                                text = chosen?.name ?: "Tap an app on the left",
                                color = if (chosen != null) TextWhite else TextGray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (chosen != null) {
                                Text(chosen.packageName, color = TextGray, fontSize = 10.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            AddGameNameField(customGameName) { customGameName = it }
                        }

                        AddGameConfirmButton(
                            enabled = canAdd,
                            onClick = confirmAdd,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AddGameHeader(onDismiss)
                    Spacer(modifier = Modifier.height(12.dp))
                    AddGameSearchField(searchQuery) { searchQuery = it }
                    Spacer(modifier = Modifier.height(10.dp))
                    AddGameAppList(
                        filteredApps = filteredApps,
                        selectedApp = selectedApp,
                        onPick = onPickApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 220.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AddGameNameField(customGameName) { customGameName = it }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AddGameConfirmButton(enabled = canAdd, onClick = confirmAdd)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddGameHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Gamepad,
                contentDescription = null,
                tint = QboostBlue,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Game to Game Space",
                color = TextWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = clickSound(onDismiss), modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = TextGray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AddGameSearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search installed games & apps...", color = Color(0xFF5A6275), fontSize = 12.sp) },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("game_search_input"),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color(0xFF1B2333),
            unfocusedContainerColor = Color(0xFF1B2333),
            cursorColor = QboostBlue
        ),
        singleLine = true
    )
}

@Composable
private fun AddGameAppList(
    filteredApps: List<InstalledAppItem>,
    selectedApp: InstalledAppItem?,
    onPick: (InstalledAppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Pick an application to boost:",
            color = TextGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0D0F16))
                .padding(6.dp)
        ) {
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No games classified by OS on this device.",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn {
                    items(filteredApps) { app ->
                        val isSelected = selectedApp?.packageName == app.packageName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) QboostBlue.copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .qClickable(enabled = !app.isAlreadyAdded) { onPick(app) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (app.isGame) QboostBlue else Color(0xFF222938)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    app.name.take(1).uppercase(),
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        app.name,
                                        color = TextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (app.isGame) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(QboostNeonCyan.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("GAME", color = QboostNeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(app.packageName, color = TextGray, fontSize = 10.sp, maxLines = 1)
                            }
                            if (app.isAlreadyAdded) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2A3447))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("ADDED", color = Color(0xFFA0AEC0), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = QboostBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddGameNameField(name: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text("Game Name", color = TextGray, fontSize = 11.sp) },
        placeholder = { Text("Custom game name...", color = Color(0xFF5A6275), fontSize = 12.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("game_name_input"),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color(0xFF1B2333),
            unfocusedContainerColor = Color(0xFF1B2333),
            cursorColor = QboostBlue
        ),
        singleLine = true
    )
}

@Composable
private fun AddGameConfirmButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = clickSound(onClick),
        colors = ButtonDefaults.buttonColors(containerColor = QboostBlue),
        shape = RoundedCornerShape(12.dp),
        enabled = enabled,
        modifier = modifier.testTag("confirm_add_game_button")
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add to Game Space", color = Color.White, fontWeight = FontWeight.Bold)
    }
}
