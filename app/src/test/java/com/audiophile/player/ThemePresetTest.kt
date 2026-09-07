package com.audiophile.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.audiophile.player.engine.SavedCustomTheme
import com.audiophile.player.ui.theme.CuratedPresets
import com.audiophile.player.ui.theme.buildVeylColorScheme
import com.audiophile.player.ui.theme.isColorLight
import com.audiophile.player.ui.theme.parseColorFromHex
import com.audiophile.player.ui.theme.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetTest {

    @Test
    fun testCuratedPresetsIntegrity() {
        assertEquals("Should have 7 curated audiophile hardware presets", 7, CuratedPresets.size)

        val expectedIds = setOf(
            "monochrome_carbon",
            "braun_rams",
            "mcintosh_blue",
            "teenage_op1",
            "sony_signature",
            "abbey_road",
            "midnight_studio"
        )

        val actualIds = CuratedPresets.map { it.id }.toSet()
        assertEquals(expectedIds, actualIds)

        CuratedPresets.forEach { preset ->
            assertNotNull(preset.name)
            assertNotNull(preset.description)
            assertTrue(preset.name.isNotEmpty())
            assertTrue(preset.description.isNotEmpty())
            // Ensure primary accent is non-transparent
            assertTrue(preset.primary.alpha > 0.9f)
            assertTrue(preset.background.alpha > 0.9f)
        }
    }

    @Test
    fun testMonochromeCarbonIsFirstDefault() {
        assertEquals("monochrome_carbon", CuratedPresets.first().id)
        assertEquals("Monochrome Carbon", CuratedPresets.first().name)
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
    fun testBuildVeylColorSchemeDarkMode() {
        val primary = Color(0xFF00F0FF)
        val secondary = Color(0xFF70A5FF)
        val tertiary = Color(0xFF39FF14)
        val background = Color(0xFF121212)

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
        assertEquals(Color(0xFFF1F5F9), scheme.textPrimary)
    }

    @Test
    fun testBuildVeylColorSchemeLightMode() {
        // Test light mode generation with white accent (Monochrome Carbon)
        val whitePrimary = Color(0xFFFFFFFF)
        val secondary = Color(0xFF94A3B8)
        val tertiary = Color(0xFF38BDF8)
        val background = Color(0xFF121212)

        val lightScheme = buildVeylColorScheme(
            primary = whitePrimary,
            secondary = secondary,
            tertiary = tertiary,
            background = background,
            isDark = false
        )

        // Background in light mode must be clean near-white canvas
        assertEquals(Color(0xFFFAFAFA), lightScheme.background)
        // High contrast text in light mode
        assertEquals(Color(0xFF09090B), lightScheme.textPrimary)
        // White primary on light background must be darkened so it remains visible
        assertNotEquals(Color.White, lightScheme.accentSignal)
        assertEquals(Color(0xFF18181B), lightScheme.accentSignal)
    }

    @Test
    fun testSavedCustomThemeModel() {
        val theme = SavedCustomTheme(
            id = "custom_12345",
            name = "Tokyo Neon",
            primaryHex = "#FF007F",
            secondaryHex = "#00F0FF",
            backgroundHex = "#0D0D15"
        )
        assertEquals("custom_12345", theme.id)
        assertEquals("Tokyo Neon", theme.name)
        assertEquals("#FF007F", theme.primaryHex)
        assertEquals("#00F0FF", theme.secondaryHex)
        assertEquals("#0D0D15", theme.backgroundHex)
        assertTrue(theme.createdAt > 0)
    }
}
