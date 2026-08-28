package de.steppicrew.healthconnectview.ui.permissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.registry.Category
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.MessageView

/**
 * Lets the user choose exactly which data types to share, rather than demanding everything.
 * Nothing is pre-ticked: the app asks for the minimum and the user opts in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    viewModel: PermissionsViewModel,
    onRequestPermissions: (Set<String>) -> Unit,
    onContinue: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Health Connect grants happen in its own UI, so state is re-read on every return.
    OnResume { viewModel.onPermissionResult() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.permissions_title)) })
        },
    ) { padding ->
        when {
            state.loading -> LoadingView(Modifier.padding(padding))

            state.availability == Availability.NotInstalled -> MessageView(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.availability_missing_title),
                body = stringResource(R.string.availability_missing_body),
                modifier = Modifier.padding(padding),
            )

            state.availability == Availability.UpdateRequired -> MessageView(
                icon = Icons.Default.Download,
                title = stringResource(R.string.availability_update_title),
                body = stringResource(R.string.availability_update_body),
                modifier = Modifier.padding(padding),
            )

            else -> PermissionList(
                state = state,
                onToggle = viewModel::toggle,
                onSelectAll = viewModel::selectAll,
                onRequest = { onRequestPermissions(viewModel.permissionsToRequest()) },
                onContinue = onContinue,
                onOpenPrivacy = onOpenPrivacy,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun PermissionList(
    state: PermissionsUiState,
    onToggle: (RecordTypeSpec<*>) -> Unit,
    onSelectAll: () -> Unit,
    onRequest: () -> Unit,
    onContinue: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.permissions_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.permissions_granted_count,
                            state.grantedCount,
                            state.grantedCount,
                            state.totalCount,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row {
                        TextButton(onClick = onSelectAll) {
                            Text(stringResource(R.string.action_select_all))
                        }
                        TextButton(onClick = onOpenPrivacy) {
                            Text(stringResource(R.string.action_privacy))
                        }
                    }
                }
            }

            RecordRegistry.byCategory.forEach { (category, specs) ->
                item(key = "header_${category.name}") {
                    CategoryHeader(category)
                }
                items(specs, key = { it.type.simpleName.orEmpty() }) { spec ->
                    PermissionRow(
                        spec = spec,
                        granted = state.isGranted(spec),
                        selected = state.isSelected(spec),
                        onToggle = { onToggle(spec) },
                    )
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onRequest,
                enabled = state.selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    pluralStringResource(
                        R.plurals.action_grant_selected,
                        state.selected.size,
                        state.selected.size,
                    ),
                )
            }
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.action_continue))
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: Category) {
    Text(
        text = stringResource(category.labelRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun PermissionRow(
    spec: RecordTypeSpec<*>,
    granted: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = granted || selected,
            onCheckedChange = { onToggle() },
            enabled = !granted,
        )
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                text = stringResource(spec.displayNameRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (granted) {
                Text(
                    text = stringResource(R.string.permission_already_granted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
