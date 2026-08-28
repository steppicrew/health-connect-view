package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Debug-only entry point for seeding synthetic data, startable from adb:
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.SeedActivity
 *
 * Only ever logs its own status, never health values.
 */
class SeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val result = runCatching { SampleDataSeeder.seed(this@SeedActivity) }
            Log.i(TAG, if (result.isSuccess) "seeded" else "seed failed: ${result.exceptionOrNull()}")
            finish()
        }
    }

    private companion object {
        const val TAG = "SampleDataSeeder"
    }
}
