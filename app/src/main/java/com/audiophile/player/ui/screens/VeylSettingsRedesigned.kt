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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.DsdModeEnum

@Composable
fun VeylSettingsRedesigned(
    controller: AudioEngineController,
    onSelectRootFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val rootPath by controller.selectedRootPath.collectAsState()
    val trackCount by controller.libraryTracks.collectAsState()
    val gapless by controller.gaplessEnabled.collectAsState()
    val lyrics by controller.autoFetchLyrics.collectAsState()
    val darkMode by controller.isDarkMode.collectAsState()
    val bitPerfect by controller.bitPerfectEnabled.collectAsState()
    val dsdMode by controller.dsdMode.collectAsState()
    val dac by controller.connectedDac.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }

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
            SettingsSection("Library") {
                SettingsRow(
                    title = "Music folder",
                    subtitle = rootPath?.substringAfterLast('/') ?: "Choose where your music lives",
                    onClick = onSelectRootFolder,
                    trailing = { Icon(VeylIcons.ChevronRight, null, tint = colors.textMuted) }
                )
                SettingsRow(
                    title = "Library",
                    subtitle = "${trackCount.size} tracks available",
                    onClick = onSelectRootFolder,
                    trailing = { Text("Rescan", style = VeylTypography.BodySmall, color = colors.accentSignal) }
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
