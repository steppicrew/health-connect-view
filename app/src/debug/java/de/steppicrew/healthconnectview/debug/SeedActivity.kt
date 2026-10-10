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
 * With `-e dashboard default|showcase` it seeds nothing and sets the dashboard instead: the
 * default layout, or one of large, coloured tiles with their curves for the store's Pro
 * screenshots, which nobody can arrange by hand on an emulator run from a script. Either way
 * the "What's new" card is put away, so no screenshot shows it.
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
                    val config = when (dashboard) {
                        "showcase" -> DashboardJson.decode(JSONArray(SHOWCASE))
                        else -> DashboardConfig.DEFAULT
                    }
                    DashboardStore(this@SeedActivity).save(config)
                    WhatsNewStore(this@SeedActivity).dismiss(Int.MAX_VALUE)
                }
            }
            val done = if (dashboard == null) "seeded" else "dashboard $dashboard"
            Log.i(TAG, if (result.isSuccess) done else "failed: ${result.exceptionOrNull()}")
            finish()
        }
    }

    private companion object {
        const val TAG = "SampleDataSeeder"

        /**
         * What Pro adds, on one screen: a year of steps as a calendar, a night's stages under
         * its heart rate, the day's workouts as curves, a weight's four weeks, and tile colours
         * throughout. The calendar needs the seeder's year of steps.
         */
        const val SHOWCASE = """[
            {"type": "StepsRecord", "color": "BLUE"},
            {"type": "HeartRateRecord", "color": "CORAL"},
            {"type": "StepsRecord", "id": "StepsRecord#2", "w": 2, "h": 1, "span": "YEAR", "face": "CALENDAR", "color": "BLUE"},
            {"type": "SleepSessionRecord", "w": 2, "h": 1, "face": "BOTH", "with": "HeartRateRecord", "color": "PURPLE"},
            {"type": "ExerciseSessionRecord", "w": 2, "h": 1, "face": "BOTH", "with": "HeartRateRecord", "color": "GREEN"},
            {"type": "WeightRecord", "w": 2, "h": 1, "span": "MONTH", "face": "BOTH", "color": "TEAL"}
        ]"""
    }
}
