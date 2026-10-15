package com.audiophile.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.online.CloudRecommendationService
import com.audiophile.player.online.JioSaavnService
import com.audiophile.player.online.OnlineArtistItem
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylTypography
import com.audiophile.player.ui.theme.isColorLight
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VeylStreamingTasteDialog(
    initialLanguages: Set<String>,
    initialArtists: Set<String>,
    onDismiss: () -> Unit,
    onSave: (languages: Set<String>, artists: Set<String>) -> Unit
) {
    val colors = LocalVeylColors.current
    val tempLangs = remember(initialLanguages) {
        val base = if (initialLanguages.isEmpty()) setOf("Malayalam", "Tamil", "English", "Hindi") else initialLanguages
        mutableStateListOf(*base.toTypedArray())
    }
    val tempArtists = remember(initialArtists) {
        val base = if (initialArtists.isEmpty()) setOf("A.R. Rahman", "Sushin Shyam", "Anirudh Ravichander") else initialArtists
        mutableStateListOf(*base.toTypedArray())
    }
    var newArtistText by remember { mutableStateOf("") }
    var artistSuggestions by remember { mutableStateOf<List<OnlineArtistItem>>(emptyList()) }

    LaunchedEffect(newArtistText) {
        val q = newArtistText.trim()
        if (q.length < 2) {
            artistSuggestions = emptyList()
        } else {
            delay(200)
            artistSuggestions = JioSaavnService.searchArtists(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    Text(
                        "Languages",
                        style = VeylTypography.TitleMedium,
                        color = colors.accentSignal,
                        fontWeight = FontWeight.Bold
                    )
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
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) colors.accentSignal else colors.borderSubtle
                                )
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
                    Text(
                        "Favorite Artists",
                        style = VeylTypography.TitleMedium,
                        color = colors.accentSignal,
                        fontWeight = FontWeight.Bold
                    )
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newArtistText,
                            onValueChange = { newArtistText = it },
                            placeholder = { Text("Search or add artist…", style = VeylTypography.BodySmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentSignal,
                                unfocusedBorderColor = colors.borderSubtle
                            )
                        )
                        Button(
                            onClick = {
                                val t = newArtistText.trim()
                                if (t.isNotBlank() && !tempArtists.contains(t)) {
                                    tempArtists.add(t)
                                    newArtistText = ""
                                }
                            },
                            enabled = newArtistText.trim().isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
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
                    onSave(tempLangs.toSet(), tempArtists.toSet())
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentSignal,
                    contentColor = btnTextColor
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Preferences", color = btnTextColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = colors.textMuted)
            }
        },
        containerColor = colors.surfacePanel,
        shape = RoundedCornerShape(20.dp)
    )
}
