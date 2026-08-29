package de.steppicrew.healthconnectview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.settings.Settings
import de.steppicrew.healthconnectview.settings.SettingsStore
import de.steppicrew.healthconnectview.ui.nav.HealthNavGraph
import de.steppicrew.healthconnectview.ui.theme.HealthConnectViewTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Read here rather than inside the theme so a change repaints the whole app at
            // once; the default matches SettingsStore's so the first frame is not a flash of
            // the wrong palette.
            val settings by remember { SettingsStore(this).settings }
                .collectAsStateWithLifecycle(initialValue = Settings())

            HealthConnectViewTheme(
                theme = settings.theme,
                dynamicColor = settings.dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val contract: ActivityResultContract<Set<String>, Set<String>> =
                        remember { PermissionController.createRequestPermissionResultContract() }
                    // The result is ignored on purpose: granted permissions are always re-read
                    // from Health Connect, which is authoritative and reflects partial grants.
                    val launcher = rememberLauncherForActivityResult(contract) { }

                    HealthNavGraph(
                        onRequestPermissions = { permissions ->
                            if (permissions.isNotEmpty()) launcher.launch(permissions)
                        },
                    )
                }
            }
        }
    }
}
