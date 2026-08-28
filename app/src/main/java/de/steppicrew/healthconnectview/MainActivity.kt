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
import de.steppicrew.healthconnectview.ui.nav.HealthNavGraph
import de.steppicrew.healthconnectview.ui.theme.HealthConnectViewTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthConnectViewTheme {
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
