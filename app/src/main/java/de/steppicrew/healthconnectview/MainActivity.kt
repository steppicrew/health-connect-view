package de.steppicrew.healthconnectview

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.nav.DebugNav
import de.steppicrew.healthconnectview.settings.Settings
import de.steppicrew.healthconnectview.settings.SettingsStore
import de.steppicrew.healthconnectview.ui.components.ExpandRequest
import de.steppicrew.healthconnectview.ui.components.LocalExpandRequest
import de.steppicrew.healthconnectview.ui.nav.HealthNavGraph
import de.steppicrew.healthconnectview.ui.theme.HealthConnectViewTheme

class MainActivity : ComponentActivity() {

    /**
     * Screen to open on instead of the dashboard, in debug builds only.
     *
     * State rather than a plain field because the activity is singleTop: a second `am start`
     * while it is already running delivers onNewIntent rather than recreating it, and without
     * recomposition the new route would be silently ignored -- which reads as the backdoor
     * not working.
     */
    private var startRoute by mutableStateOf<String?>(null)

    /**
     * A chart to open full screen, from the debug backdoor; null in release. Cleared once a
     * chart has taken it, and read only on a fresh start -- opening full screen turns the
     * screen, the turn recreates the activity, and a second reading would reopen the chart
     * every time it was closed.
     */
    private var expandChart by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startRoute = DebugNav.startRoute(intent)
        expandChart = DebugNav.expandChart(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Declared rather than inherited: from targetSdk 35 the platform draws edge-to-edge
        // regardless, and opting in explicitly is what makes the bars' scrim and icon tint
        // ours to set. The tint itself follows the in-app theme, in HealthConnectViewTheme.
        enableEdgeToEdge()
        // Only on a fresh start: a recreation -- a turn of the screen -- would otherwise push the
        // route again, on top of the screen already showing it.
        if (savedInstanceState == null) {
            startRoute = DebugNav.startRoute(intent)
            expandChart = DebugNav.expandChart(intent)
        }
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

                    CompositionLocalProvider(LocalExpandRequest provides ExpandRequest(expandChart) { expandChart = null }) {
                        HealthNavGraph(
                            onRequestPermissions = { permissions ->
                                if (permissions.isNotEmpty()) launcher.launch(permissions)
                            },
                            // Null in release, where DebugNav has no implementation to read it.
                            startRoute = startRoute,
                        )
                    }
                }
            }
        }
    }
}
