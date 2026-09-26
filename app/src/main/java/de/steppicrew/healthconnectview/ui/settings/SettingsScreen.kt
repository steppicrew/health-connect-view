package de.steppicrew.healthconnectview.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.LaunchedEffect
import java.time.LocalDate
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import androidx.activity.compose.LocalActivity
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.ProState
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.settings.ThemeChoice
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.util.appLabelFor

/**
 * Preferences, and the doors into the settings that belong to Android or Health Connect
 * rather than to this app.
 *
 * Language and data-source priority are deliberately links rather than controls: both are
 * owned by the platform, and a private copy here would drift from what the system actually
 * does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val granted by viewModel.grantedCount.collectAsStateWithLifecycle()
    val writers by viewModel.writers.collectAsStateWithLifecycle()
    val preferredSource by viewModel.preferredSource.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmRevoke by remember { mutableStateOf(false) }
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.backupEvents.collect { event ->
            snackbar.showSnackbar(
                resources.getString(
                    when (event) {
                        BackupEvent.Exported -> R.string.backup_exported
                        BackupEvent.Restored -> R.string.backup_restored
                        BackupEvent.NotABackup -> R.string.backup_not_a_backup
                        BackupEvent.NewerFormat -> R.string.backup_newer
                        BackupEvent.Failed -> R.string.backup_failed
                    },
                ),
            )
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    // Any type, not only JSON: file managers and cloud drives often label a .json as plain text
    // or octet-stream, and a filter that hid the user's own backup would be a dead end. The
    // content is checked when read.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::readBackup) }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.backup_confirm_body,
                        backup.dashboard.tiles.size,
                        backup.dashboard.tiles.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text(stringResource(R.string.backup_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    // Access can be changed in Health Connect while this screen is backgrounded, and a purchase
    // can be completed or refunded in the Play Store.
    OnResume {
        viewModel.refresh()
        AppEntitlements.current.refresh()
    }

    if (confirmRevoke) {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text(stringResource(R.string.settings_revoke_confirm)) },
            text = { Text(stringResource(R.string.settings_revoke_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRevoke = false
                        viewModel.revokeAll { }
                    },
                ) {
                    Text(stringResource(R.string.settings_revoke))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevoke = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = settings.theme == choice,
                            onClick = { viewModel.setTheme(choice) },
                            label = { Text(stringResource(choice.labelRes())) },
                        )
                    }
                }
            }

            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                body = stringResource(R.string.settings_dynamic_color_body),
                checked = settings.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )

            // Per-app language is an Android 13+ platform feature; below that the app follows
            // the system language and there is nothing to link to.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LinkRow(
                    title = stringResource(R.string.settings_language),
                    body = stringResource(R.string.settings_language_body),
                    onClick = { context.openAppLanguageSettings() },
                )
            }

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_data))

            // This app's own picker comes first: it is the one that actually requests
            // permissions, and on a first run it is the only route to granting anything.
            LinkRow(
                title = stringResource(R.string.settings_choose_data),
                body = pluralStringResource(
                    R.plurals.settings_choose_data_body,
                    RecordRegistry.allReadPermissions.size,
                    RecordRegistry.allReadPermissions.size,
                ),
                onClick = onOpenPermissions,
            )

            LinkRow(
                title = stringResource(R.string.settings_manage_access),
                body = stringResource(R.string.settings_manage_access_body),
                onClick = { context.openHealthConnectSettings() },
            )

            // Offered only once there is a choice to make: with a single writer the preference
            // has no effect, and an empty menu would be a dead end.
            if (writers.size > 1) {
                PreferredSourceRow(
                    writers = writers,
                    preferred = preferredSource,
                    onSelect = viewModel::preferSource,
                )
            }

            // The overlap winner is Health Connect's own priority list, which is not readable
            // or writable through the Jetpack client -- so this points at it rather than
            // inventing a ranking that would disagree with the platform.
            LinkRow(
                title = stringResource(R.string.settings_app_priority),
                body = stringResource(R.string.settings_app_priority_body),
                onClick = { context.openHealthConnectSettings() },
            )

            if (granted > 0) {
                LinkRow(
                    title = stringResource(R.string.settings_revoke),
                    body = stringResource(R.string.settings_revoke_body),
                    onClick = { confirmRevoke = true },
                    danger = true,
                )
            }

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_backup))

            // Free, unlike the health-data export: nothing here is health data, and losing a
            // hand-arranged dashboard on a phone switch is exactly what this prevents.
            LinkRow(
                title = stringResource(R.string.settings_backup_export),
                body = stringResource(R.string.settings_backup_export_body),
                onClick = { exportLauncher.launch("health-connect-view-settings-${LocalDate.now()}.json") },
            )
            LinkRow(
                title = stringResource(R.string.settings_backup_import),
                body = stringResource(R.string.settings_backup_import_body),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_pro))
            ProRow(pro)

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_about))

            LinkRow(
                title = stringResource(R.string.settings_privacy),
                body = null,
                onClick = onOpenPrivacy,
            )
            LinkRow(
                title = stringResource(R.string.settings_source),
                body = SOURCE_URL,
                onClick = { context.openUrl(SOURCE_URL) },
            )

            Text(
                text = stringResource(R.string.settings_version, context.versionName()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Picks the app shown where a tile has no per-type choice of its own.
 *
 * Deliberately not called a priority: it cannot change which record wins where two overlap --
 * that is Health Connect's own list, which this app cannot write. It selects whose data is
 * displayed, so the number shown becomes that app's figure rather than the deduplicated one,
 * and the row says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferredSourceRow(
    writers: List<String>,
    preferred: String?,
    onSelect: (String?) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val allSources = stringResource(R.string.settings_preferred_source_all)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_preferred_source),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = preferred?.let { context.appLabelFor(it) } ?: allSources,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.settings_preferred_source_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allSources) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            writers.forEach { writer ->
                DropdownMenuItem(
                    text = { Text(context.appLabelFor(writer)) },
                    onClick = {
                        onSelect(writer)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * The Pro unlock: bought, waiting on a slow payment, on offer, or out of reach.
 *
 * The price is Play's own string for the user's country; without one there is nothing to buy,
 * so the row offers a retry rather than a button that cannot start a purchase.
 */
@Composable
private fun ProRow(pro: ProState) {
    val activity = LocalActivity.current
    when {
        pro.owned -> LinkRow(
            title = stringResource(R.string.pro_owned),
            body = stringResource(R.string.pro_owned_body),
            onClick = null,
        )
        pro.pending -> LinkRow(
            title = stringResource(R.string.pro_pending),
            body = stringResource(R.string.pro_pending_body),
            onClick = null,
        )
        pro.price != null -> LinkRow(
            title = stringResource(R.string.pro_buy, pro.price),
            body = stringResource(R.string.pro_body),
            onClick = { activity?.let(AppEntitlements.current::buy) },
        )
        else -> LinkRow(
            title = stringResource(R.string.settings_pro),
            body = stringResource(R.string.pro_unavailable_body),
            onClick = AppEntitlements.current::refresh,
        )
    }
}

@Composable
private fun LinkRow(
    title: String,
    body: String?,
    onClick: (() -> Unit)?,
    danger: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        body?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ThemeChoice.labelRes(): Int = when (this) {
    ThemeChoice.SYSTEM -> R.string.settings_theme_system
    ThemeChoice.LIGHT -> R.string.settings_theme_light
    ThemeChoice.DARK -> R.string.settings_theme_dark
}

/**
 * Opens Android's per-app language screen for this app.
 *
 * Falls back to the app's own settings page: the per-app language screen is not guaranteed to
 * exist on every device even at API 33, and a dead button is worse than a general one.
 */
private fun Context.openAppLanguageSettings() {
    // The literal rather than Settings.ACTION_APP_LOCALE_SETTINGS: the constant is inlined at
    // compile time, so referencing it trips minSdk lint even behind a version check. The
    // action string itself is stable platform API.
    val specific = Intent(ACTION_APP_LOCALE_SETTINGS)
        .setData("package:$packageName".toUri())
    val fallback = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:$packageName".toUri())
    startActivitySafely(specific, fallback)
}

private fun Context.openHealthConnectSettings() {
    startActivitySafely(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
}

/**
 * Version from the package manager rather than BuildConfig, which is not generated for this
 * module -- enabling it for a single string would add a build feature for no other reason.
 */
private fun Context.versionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
        .getOrNull()
        .orEmpty()

private fun Context.openUrl(url: String) {
    startActivitySafely(Intent(Intent.ACTION_VIEW, url.toUri()))
}

/**
 * Tries each intent in turn. Every target here belongs to another app that may be absent,
 * disabled, or differently named by an OEM, so an unhandled intent must not crash the app.
 */
private fun Context.startActivitySafely(vararg intents: Intent) {
    intents.forEach { intent ->
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}

private const val ACTION_APP_LOCALE_SETTINGS = "android.settings.APP_LOCALE_SETTINGS"

private const val SOURCE_URL = "https://github.com/steppicrew/health-connect-view"
