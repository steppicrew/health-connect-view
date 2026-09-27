package de.steppicrew.healthconnectview.ui.permissions

import androidx.annotation.StringRes
import androidx.health.connect.client.HealthConnectClient
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.content.Context
import de.steppicrew.healthconnectview.ui.components.InfoToggle
import de.steppicrew.healthconnectview.ui.components.ExplanationState
import de.steppicrew.healthconnectview.registry.PermissionInfo
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
                onToggleHistory = viewModel::toggleHistory,
                onToggleRoutes = viewModel::toggleRoutes,
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
    onToggleHistory: () -> Unit,
    onToggleRoutes: () -> Unit,
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
                    // Android lets an app give back all its access at once, never one permission:
                    // that switch is Health Connect's. A granted row therefore opens this app's
                    // page there, and the list re-reads what is granted on return.
                    Text(
                        text = stringResource(R.string.permissions_revoke_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
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

            item(key = "history") {
                ExtraPermissionRow(
                    title = R.string.permission_history_title,
                    body = R.string.permission_history_body,
                    info = R.string.perm_info_history,
                    granted = state.historyGranted,
                    selected = state.historySelected,
                    onToggle = onToggleHistory,
                )
                ExtraPermissionRow(
                    title = R.string.permission_routes_title,
                    body = R.string.permission_routes_body,
                    info = R.string.perm_info_routes,
                    granted = state.routesGranted,
                    selected = state.routesSelected,
                    onToggle = onToggleRoutes,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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

/**
 * A permission that is not a record type, above the type list rather than inside a category.
 *
 * History is depth: Health Connect caps reads at 30 days without it and reports no error,
 * which reads as "there is no older data" instead of "the app may not see it". Routes are
 * every exercise track at once; without them each one is asked for on its own.
 */
@Composable
private fun ExtraPermissionRow(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes info: Int,
    granted: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    PermissionRowLayout(
        title = stringResource(title),
        detail = if (granted) null else stringResource(body),
        info = info,
        granted = granted,
        selected = selected,
        onToggle = onToggle,
    )
}

@Composable
private fun PermissionRow(
    spec: RecordTypeSpec<*>,
    granted: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    // Types sharing one permission tick together -- Health Connect grants the permission, not
    // the type -- so the row says so up front rather than leaving a second box to tick itself.
    val siblings = RecordRegistry.all
        .filter { it.permission == spec.permission && it.type != spec.type }
        .map { stringResource(it.displayNameRes) }
    PermissionRowLayout(
        title = stringResource(spec.displayNameRes),
        detail = if (siblings.isEmpty()) null else stringResource(R.string.permission_shared_with, siblings.joinToString(", ")),
        info = PermissionInfo.infoFor(spec),
        granted = granted,
        selected = selected,
        onToggle = onToggle,
    )
}

/**
 * One permission: a box to tick, its name, a line beneath (what it is, or who it is shared
 * with), "Freigegeben" once granted, and an "i" that opens what the app does with it.
 *
 * The "i" is open per row and forgotten on leaving the screen: this is read once while
 * deciding, not a setting to remember.
 */
@Composable
private fun PermissionRowLayout(
    title: String,
    detail: String?,
    @StringRes info: Int?,
    granted: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = if (granted) context::openOwnHealthPermissions else onToggle)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            // Box and "i" on the name's line, not in the middle of a row that grew a second
            // and third line beneath it.
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = granted || selected,
                // Kept even when granted: without a callback the box drops its 48 dp target and
                // shifts off the others' line. Disabled, it passes the tap on to the row.
                onCheckedChange = { onToggle() },
                enabled = !granted,
            )
            // The checkbox's touch target is 48 dp with the box in its middle; this puts the
            // first line's centre on the box's.
            Column(Modifier.weight(1f).padding(start = 8.dp, top = CHECKBOX_LINE_OFFSET.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (granted) {
                    Text(
                        text = stringResource(R.string.permission_already_granted),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (info != null) InfoToggle(ExplanationState(open) { open = !open })
        }
        if (open && info != null) {
            Column(Modifier.padding(start = 64.dp, end = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.perm_info_closing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Half the checkbox's 48 dp target minus half a body line, so the name sits beside the box. */
private const val CHECKBOX_LINE_OFFSET = 12

/**
 * This app's own page in Health Connect, where each permission has its switch; the general
 * settings where that page does not exist (Health Connect before Android 14).
 */
private fun Context.openOwnHealthPermissions() {
    // The literal rather than HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS, which is
    // inlined at compile time and trips minSdk lint even behind a version check.
    val own = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
        .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
    val general = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    listOf(own, general).forEach { intent ->
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}
