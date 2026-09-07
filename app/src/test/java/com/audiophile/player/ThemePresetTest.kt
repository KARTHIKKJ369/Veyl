package com.audiophile.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.audiophile.player.ui.theme.CuratedPresets
import com.audiophile.player.ui.theme.buildVeylColorScheme
import com.audiophile.player.ui.theme.isColorLight
import com.audiophile.player.ui.theme.parseColorFromHex
import com.audiophile.player.ui.theme.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetTest {

    @Test
    fun testCuratedPresetsIntegrity() {
        assertEquals("Should have 7 curated presets", 7, CuratedPresets.size)

        val expectedIds = setOf(
            "resonate_obsidian",
            "oled_pure_black",
            "cyberpunk_amber",
            "nordic_slate",
            "emerald_studio",
            "monochrome_carbon",
            "royal_violet"
        )

        val actualIds = CuratedPresets.map { it.id }.toSet()
        assertEquals(expectedIds, actualIds)

        CuratedPresets.forEach { preset ->
            assertNotNull(preset.name)
            assertNotNull(preset.description)
            assertTrue(preset.name.isNotEmpty())
            assertTrue(preset.description.isNotEmpty())
        }
    }

    @Test
    fun testHexConversion() {
        val color = Color(0xFFFFB7B4)
        val hex = color.toHex()
        assertEquals("#FFB7B4", hex)

        val parsed = parseColorFromHex("#FFB7B4", Color.Black)
        assertEquals(0xFFFFB7B4.toInt(), parsed.toArgb())

        val fallback = Color(0xFF123456)
        val malformed = parseColorFromHex("not_a_color", fallback)
        assertEquals(fallback, malformed)
    }

    @Test
    fun testIsColorLight() {
        assertTrue("White should be light", isColorLight(Color.White))
        assertFalse("Black should be dark", isColorLight(Color.Black))
        assertFalse("Obsidian #1B0906 should be dark", isColorLight(Color(0xFF1B0906)))
        assertTrue("Bright yellow should be light", isColorLight(Color(0xFFFFFF00)))
    }

    @Test
    fun testBuildVeylColorScheme() {
        val primary = Color(0xFF00F0FF)
        val secondary = Color(0xFF70A5FF)
        val tertiary = Color(0xFF39FF14)
        val background = Color(0xFF000000)

        val scheme = buildVeylColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            background = background,
            isDark = true
        )

        assertEquals(background, scheme.background)
        assertEquals(primary, scheme.accentSignal)
        assertEquals(secondary, scheme.textMono)
        assertEquals(tertiary, scheme.accentCyan)
        assertNotNull(scheme.surfacePanel)
        assertNotNull(scheme.surfaceElevated)
        assertNotNull(scheme.surfacePill)
        assertNotNull(scheme.textPrimary)
    }
}
