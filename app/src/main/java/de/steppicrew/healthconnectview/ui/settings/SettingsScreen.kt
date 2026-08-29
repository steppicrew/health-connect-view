package de.steppicrew.healthconnectview.ui.settings

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
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.settings.ThemeChoice
import de.steppicrew.healthconnectview.ui.components.OnResume

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
    val context = LocalContext.current
    var confirmRevoke by remember { mutableStateOf(false) }

    // Access can be changed in Health Connect while this screen is backgrounded.
    OnResume { viewModel.refresh() }

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

@Composable
private fun LinkRow(
    title: String,
    body: String?,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

private const val SOURCE_URL = "https://github.com/steppicrew/healthData"
