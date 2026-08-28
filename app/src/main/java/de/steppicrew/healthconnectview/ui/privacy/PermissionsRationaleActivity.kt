package de.steppicrew.healthconnectview.ui.privacy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.steppicrew.healthconnectview.ui.theme.HealthConnectViewTheme

/**
 * Shown when Health Connect asks why this app wants access. Both the pre-Android-14 action
 * and the 14+ activity-alias route here.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthConnectViewTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PrivacyScreen()
                }
            }
        }
    }
}
