package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.dashboard.DashboardConfig
import de.steppicrew.healthconnectview.dashboard.DashboardJson
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.settings.WhatsNewStore
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Debug-only entry point for seeding synthetic data, startable from adb:
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.SeedActivity
 *
 * With `-e dashboard default|showcase|night` it seeds nothing and sets the dashboard instead: the
 * default layout, or one of large, coloured tiles with their curves for the store's Pro
 * screenshots, which nobody can arrange by hand on an emulator run from a script. Either way
 * the "What's new" card is put away, so no screenshot shows it.
 * A JSON array of tiles instead, as in [SHOWCASE], sets that layout: a tile the showcase lacks.
 *
 * Only ever logs its own status, never health values.
 */
class SeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dashboard = intent?.getStringExtra("dashboard")
        lifecycleScope.launch {
            val result = runCatching {
                if (dashboard == null) {
                    SampleDataSeeder.seed(this@SeedActivity)
                } else {
                    val config = when {
                        dashboard == "showcase" -> DashboardJson.decode(JSONArray(SHOWCASE))
                        dashboard == "night" -> DashboardJson.decode(JSONArray(NIGHT))
                        // A layout of its own, for a tile the showcase does not have.
                        dashboard.startsWith("[") -> DashboardJson.decode(JSONArray(dashboard))
                        else -> DashboardConfig.DEFAULT
                    }
                    DashboardStore(this@SeedActivity).save(config)
                    WhatsNewStore(this@SeedActivity).dismiss(Int.MAX_VALUE)
                }
            }
            val done = if (dashboard == null) "seeded" else "dashboard ${dashboard.take(LOG_NAME)}"
            Log.i(TAG, if (result.isSuccess) done else "failed: ${result.exceptionOrNull()}")
            finish()
        }
    }

    private companion object {
        const val TAG = "SampleDataSeeder"
        const val LOG_NAME = 20

        /**
         * The first Pro shot, the owner's own dashboard top (12.10.2026): the week's changes,
         * steps and floors as rings past their goals, the day's steps against an adaptive goal,
         * and the day's workouts as heart-rate curves, in tile colours.
         */
        const val SHOWCASE = """[
            {"type": "Insights", "w": 2, "h": 1},
            {"type": "StepsRecord", "id": "StepsRecord#2", "goal": 5000, "adaptiveGoal": true, "color": "BLUE"},
            {"type": "FloorsClimbedRecord", "color": "TEAL"},
            {"type": "StepsRecord", "w": 2, "h": 1, "goal": 5000, "adaptiveGoal": true, "face": "CHART", "color": "BLUE"},
            {"type": "ExerciseSessionRecord", "w": 2, "h": 1, "face": "BOTH", "with": "HeartRateRecord", "color": "GREEN"}
        ]"""

        /**
         * The second, further down the owner's dashboard: the night's stages under its heart
         * rate, a year of nights as a calendar, the day's heart rate with its mean, and a month
         * of HRV with its status. The calendar needs the seeder's year of sleep.
         */
        const val NIGHT = """[
            {"type": "SleepSessionRecord", "w": 2, "h": 1, "face": "BOTH", "with": "HeartRateRecord", "color": "BLUE"},
            {"type": "SleepSessionRecord", "id": "SleepSessionRecord#2", "w": 2, "h": 1, "span": "YEAR", "face": "CALENDAR", "color": "BLUE"},
            {"type": "HeartRateRecord", "w": 2, "h": 1, "face": "BOTH", "mean": true},
            {"type": "HeartRateVariabilityRmssdRecord", "w": 2, "h": 1, "span": "MONTH", "face": "BOTH"}
        ]"""
    }
}
