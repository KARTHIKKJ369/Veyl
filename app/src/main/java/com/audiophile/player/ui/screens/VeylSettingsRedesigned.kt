package com.audiophile.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AppExperienceMode
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.online.CloudRecommendationService
import androidx.compose.runtime.LaunchedEffect
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import com.audiophile.player.ui.theme.isColorLight
import uniffi.audiophile_core.DsdModeEnum

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VeylSettingsRedesigned(
    controller: AudioEngineController,
    onSelectRootFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val appExperienceMode by controller.appExperienceMode.collectAsState()
    val selectedLanguages by controller.selectedLanguages.collectAsState()
    val selectedArtists by controller.selectedArtists.collectAsState()
    val rootPath by controller.selectedRootPath.collectAsState()
    val isScanning by controller.isScanning.collectAsState()
    val trackCount by controller.libraryTracks.collectAsState()
    val gapless by controller.gaplessEnabled.collectAsState()
    val lyrics by controller.autoFetchLyrics.collectAsState()
    val darkMode by controller.isDarkMode.collectAsState()
    val bitPerfect by controller.bitPerfectEnabled.collectAsState()
    val dsdMode by controller.dsdMode.collectAsState()
    val dac by controller.connectedDac.collectAsState()
    val isAutoplay by controller.isAutoplayEnabled.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }
    var showTasteDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.background).statusBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                Text("Settings", style = VeylTypography.DisplayLarge, color = colors.textPrimary)
                Text("Make Veyl feel right for you.", style = VeylTypography.BodySmall, color = colors.textMuted)
            }
        }
        item {
            SettingsSection("App Experience") {
                SettingsRow(
                    title = "Experience Mode",
                    subtitle = if (appExperienceMode == AppExperienceMode.CLOUD_FIRST)
                        "Cloud First (Streaming Discovery)"
                    else
                        "Local First (Audiophile Storage)",
                    onClick = {
                        val newMode = if (appExperienceMode == AppExperienceMode.CLOUD_FIRST)
                            AppExperienceMode.LOCAL_FIRST
                        else
                            AppExperienceMode.CLOUD_FIRST
                        controller.setAppExperienceMode(newMode)
                    },
                    trailing = {
                        Text(
                            text = if (appExperienceMode == AppExperienceMode.CLOUD_FIRST) "Cloud" else "Local",
                            style = VeylTypography.BodySmall,
                            color = colors.accentSignal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                if (appExperienceMode == AppExperienceMode.CLOUD_FIRST) {
                    SettingsRow(
                        title = "Continuous Autoplay Radio",
                        subtitle = if (isAutoplay) "Keep playing similar songs infinitely" else "Stop playback when current queue ends",
                        onClick = { controller.toggleAutoplay() },
                        trailing = {
                            Text(
                                text = if (isAutoplay) "On" else "Off",
                                style = VeylTypography.BodySmall,
                                color = if (isAutoplay) colors.accentSignal else colors.textMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    SettingsRow(
                        title = "Streaming Preferences",
                        subtitle = "${selectedLanguages.joinToString(", ")} • ${selectedArtists.size} artists",
                        onClick = { showTasteDialog = true },
                        trailing = { Icon(VeylIcons.ChevronRight, null, tint = colors.textMuted) }
                    )
                }
                SettingsRow(
                    title = "Reset Onboarding",
                    subtitle = "Re-run the first launch setup walkthrough",
                    onClick = { controller.resetOnboarding() },
                    trailing = { Text("Reset", style = VeylTypography.BodySmall, color = colors.textMuted) }
                )
            }
        }
        item {
            SettingsSection("Library") {
                SettingsRow(
                    title = "Music folder",
                    subtitle = rootPath?.substringAfterLast('/') ?: "Choose where your music lives",
                    onClick = onSelectRootFolder,
                    trailing = { Icon(VeylIcons.ChevronRight, null, tint = colors.textMuted) }
                )
                SettingsRow(
                    title = "Rescan library",
                    subtitle = if (isScanning) "Scanning audio storage…" else "${trackCount.size} tracks available",
                    onClick = { controller.rescanLibrary() },
                    trailing = {
                        if (isScanning) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = colors.accentSignal,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Rescan", style = VeylTypography.BodySmall, color = colors.accentSignal)
                        }
                    }
                )
            }
        }
        item {
            SettingsSection("Listening") {
                SettingsSwitch("Gapless playback", "Move between tracks without a pause", gapless, controller::setGaplessEnabled)
                SettingsSwitch("Find synced lyrics", "Fetch lyrics when available", lyrics, controller::setAutoFetchLyrics)
            }
        }
        item {
            SettingsSection("Appearance") {
                SettingsSwitch("Dark appearance", "Use Veyl’s dark listening environment", darkMode, controller::setDarkMode)
            }
        }
        item {
            SettingsSection("Advanced audio") {
                SettingsRow(
                    title = "Audio hardware",
                    subtitle = dac?.name ?: "Phone speaker or system audio",
                    onClick = { showAdvanced = !showAdvanced },
                    trailing = { Icon(if (showAdvanced) VeylIcons.ChevronDown else VeylIcons.ChevronRight, null, tint = colors.textMuted) }
                )
                if (showAdvanced) {
                    SettingsSwitch("Bit-perfect output", "Use the source sample rate with a compatible USB DAC", bitPerfect, controller::setBitPerfectEnabled)
                    SettingsRow(
                        title = "DSD playback",
                        subtitle = if (dsdMode == DsdModeEnum.DO_P) "DoP" else "PCM conversion",
                        onClick = {
                            controller.setDsdMode(if (dsdMode == DsdModeEnum.DO_P) DsdModeEnum.PCM_DECIMATION else DsdModeEnum.DO_P)
                        },
                        trailing = { Text("Change", style = VeylTypography.BodySmall, color = colors.accentSignal) }
                    )
                }
            }
        }
        item {
            Text(
                "Reset listening preferences",
                style = VeylTypography.BodySmall,
                color = colors.textMuted,
                modifier = Modifier.clickable { controller.resetSettingsToDefault() }.padding(vertical = 12.dp)
            )
        }
    }

    if (showTasteDialog) {
        val tempLangs = remember(selectedLanguages) { mutableStateListOf(*selectedLanguages.toTypedArray()) }
        val tempArtists = remember(selectedArtists) { mutableStateListOf(*selectedArtists.toTypedArray()) }
        var newArtistText by remember { mutableStateOf("") }
        var artistSuggestions by remember { mutableStateOf<List<com.audiophile.player.online.OnlineArtistItem>>(emptyList()) }

        LaunchedEffect(newArtistText) {
            val q = newArtistText.trim()
            if (q.length < 2) {
                artistSuggestions = emptyList()
            } else {
                kotlinx.coroutines.delay(200)
                artistSuggestions = com.audiophile.player.online.JioSaavnService.searchArtists(q)
            }
        }

        AlertDialog(
            onDismissRequest = { showTasteDialog = false },
            title = {
                Text(
                    "Streaming Preferences",
                    style = VeylTypography.HeadlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text("Languages", style = VeylTypography.TitleMedium, color = colors.accentSignal, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CloudRecommendationService.defaultLanguages.forEach { lang ->
                                val isSelected = tempLangs.contains(lang)
                                Surface(
                                    modifier = Modifier
                                        .clickable {
                                            if (isSelected) {
                                                if (tempLangs.size > 1) tempLangs.remove(lang)
                                            } else {
                                                tempLangs.add(lang)
                                            }
                                        }
                                        .clip(RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) colors.accentSignal.copy(alpha = 0.2f) else colors.surfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) colors.accentSignal else colors.borderSubtle)
                                ) {
                                    Text(
                                        text = lang,
                                        style = VeylTypography.BodySmall,
                                        color = if (isSelected) colors.accentSignal else colors.textPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text("Favorite Artists", style = VeylTypography.TitleMedium, color = colors.accentSignal, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tempArtists.forEach { artistName ->
                                Surface(
                                    modifier = Modifier
                                        .clickable {
                                            tempArtists.remove(artistName)
                                        }
                                        .clip(RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    color = colors.accentSignal.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentSignal)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = artistName,
                                            style = VeylTypography.BodySmall,
                                            color = colors.accentSignal,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "✕",
                                            style = VeylTypography.BodySmall,
                                            color = colors.accentSignal,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newArtistText,
                                onValueChange = { newArtistText = it },
                                placeholder = { Text("Search or add artist…", style = VeylTypography.BodySmall, color = colors.textMuted) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentSignal,
                                    unfocusedBorderColor = colors.borderSubtle,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val trimmed = newArtistText.trim()
                                    if (trimmed.isNotBlank() && !tempArtists.contains(trimmed)) {
                                        tempArtists.add(trimmed)
                                        newArtistText = ""
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated, contentColor = colors.textPrimary)
                            ) {
                                Text("Add")
                            }
                        }

                        // Search recommendations
                        if (artistSuggestions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceElevated)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                artistSuggestions.take(4).forEach { sug ->
                                    val isAdded = tempArtists.contains(sug.name)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!isAdded) {
                                                    tempArtists.add(sug.name)
                                                    newArtistText = ""
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = sug.name,
                                            style = VeylTypography.BodySmall,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (isAdded) "✓ Added" else "＋ Add",
                                            style = VeylTypography.BodySmall,
                                            color = colors.accentSignal,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val isLight = isColorLight(colors.accentSignal)
                val btnTextColor = if (isLight) Color(0xFF0F0F0F) else Color.White
                Button(
                    onClick = {
                        controller.updateMusicPreferences(tempLangs.toSet(), tempArtists.toSet())
                        CloudRecommendationService.clearCache()
                        showTasteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentSignal,
                        contentColor = btnTextColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = btnTextColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTasteDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            },
            containerColor = colors.surfacePanel,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val colors = LocalVeylColors.current
    Column {
        Text(title, style = VeylTypography.BodySmall, fontWeight = FontWeight.SemiBold, color = colors.accentSignal, fontSize = 12.sp)
        Spacer(Modifier.padding(top = 7.dp))
        content()
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val colors = LocalVeylColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = VeylTypography.BodyMedium, color = colors.textPrimary)
                Text(subtitle, style = VeylTypography.BodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        HorizontalDivider(color = colors.borderHairline)
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalVeylColors.current
    SettingsRow(title, subtitle, onClick = { onCheckedChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.background, checkedTrackColor = colors.accentSignal)
        )
    }
}
