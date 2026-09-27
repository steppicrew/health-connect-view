package de.steppicrew.healthconnectview.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    // A Scaffold like every other screen: without it the text ran under the status bar when
    // scrolled and its last line stayed behind the navigation bar.
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PrivacyText(Modifier.padding(padding))
    }
}

@Composable
private fun PrivacyText(modifier: Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {

        listOf(
            R.string.privacy_summary to null,
            R.string.privacy_no_network_title to R.string.privacy_no_network_body,
            R.string.privacy_read_only_title to R.string.privacy_read_only_body,
            R.string.privacy_storage_title to R.string.privacy_storage_body,
            R.string.privacy_sharing_title to R.string.privacy_sharing_body,
            R.string.privacy_routes_title to R.string.privacy_routes_body,
            R.string.privacy_control_title to R.string.privacy_control_body,
            R.string.privacy_purchases_title to R.string.privacy_purchases_body,
            R.string.privacy_contact_title to R.string.privacy_contact_body,
        ).forEach { (titleRes, bodyRes) ->
            if (bodyRes == null) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
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
