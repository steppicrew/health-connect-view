package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.dashboard.DashboardConfig
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.settings.BackupError
import de.steppicrew.healthconnectview.settings.Settings
import de.steppicrew.healthconnectview.settings.SettingsBackup
import de.steppicrew.healthconnectview.settings.SettingsBackupCodec
import de.steppicrew.healthconnectview.settings.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class SettingsBackupTest {

    private val backup = SettingsBackup(
        dashboard = DashboardConfig(
            listOf(
                Tile("StepsRecord", goal = 8000.0),
                Tile("HeartRateRecord", zones = ValueZones(listOf(60.0, 100.0, 140.0))),
                Tile("WeightRecord"),
            ),
        ),
        sourceSelections = mapOf("StepsRecord" to "com.garmin.android.apps.connectmobile"),
        preferredSource = "nl.appyhapps.healthsync",
        settings = Settings(theme = ThemeChoice.DARK, dynamicColor = false, expandedExplanations = setOf("trend")),
    )

    private fun roundTrip(text: String) = SettingsBackupCodec.decode(text)

    @Test
    fun `a backup survives the round trip unchanged`() {
        val text = SettingsBackupCodec.encode(backup, LocalDate.of(2026, 9, 26))
        assertEquals(backup, roundTrip(text))
    }

    @Test
    fun `the file names itself and its format`() {
        val text = SettingsBackupCodec.encode(backup, LocalDate.of(2026, 9, 26))
        assertTrue(text.contains("\"kind\": \"health-connect-view-settings\""))
        assertTrue(text.contains("\"format\": 1"))
    }

    @Test
    fun `unrelated JSON is refused, not misread`() {
        expect<BackupError.NotABackup> { roundTrip("""{"tiles": []}""") }
        expect<BackupError.NotABackup> { roundTrip("not json at all") }
    }

    @Test
    fun `a newer format is refused rather than half applied`() {
        expect<BackupError.NewerFormat> {
            roundTrip("""{"kind": "health-connect-view-settings", "format": 2}""")
        }
    }

    @Test
    fun `unknown tile types are dropped and odd fields fall back`() {
        val restored = roundTrip(
            """
            {"kind": "health-connect-view-settings", "format": 1,
             "dashboard": [{"type": "StepsRecord"}, {"type": "NoSuchRecord"}, {"w": 2}],
             "settings": {"theme": "PURPLE", "dynamicColor": false}}
            """,
        )
        assertEquals(listOf("StepsRecord"), restored.dashboard.tiles.map { it.typeName })
        assertEquals(ThemeChoice.SYSTEM, restored.settings.theme)
        assertEquals(false, restored.settings.dynamicColor)
        assertNull(restored.preferredSource)
        assertTrue(restored.sourceSelections.isEmpty())
    }

    @Test
    fun `a backup without a dashboard restores the default one`() {
        val restored = roundTrip("""{"kind": "health-connect-view-settings", "format": 1}""")
        assertEquals(DashboardConfig.DEFAULT.tiles.map { it.typeName }, restored.dashboard.tiles.map { it.typeName })
    }

    private inline fun <reified E : Throwable> expect(block: () -> Unit) {
        try {
            block()
            fail("expected ${E::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is E) throw e
        }
    }
}
