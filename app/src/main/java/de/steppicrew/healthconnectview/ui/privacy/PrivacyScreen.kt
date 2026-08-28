package de.steppicrew.healthconnectview.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R

/**
 * The privacy policy. Health Connect requires apps to show one, and it is rendered locally
 * rather than linked, because the app has no network access to fetch it with.
 */
@Composable
fun PrivacyScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.privacy_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        listOf(
            R.string.privacy_summary to null,
            R.string.privacy_no_network_title to R.string.privacy_no_network_body,
            R.string.privacy_read_only_title to R.string.privacy_read_only_body,
            R.string.privacy_storage_title to R.string.privacy_storage_body,
            R.string.privacy_sharing_title to R.string.privacy_sharing_body,
            R.string.privacy_control_title to R.string.privacy_control_body,
            R.string.privacy_purchases_title to R.string.privacy_purchases_body,
            R.string.privacy_contact_title to R.string.privacy_contact_body,
        ).forEach { (titleRes, bodyRes) ->
            if (bodyRes == null) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
