package de.steppicrew.healthconnectview.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Every supported type is listed, always — including ones with no data and ones the user has
 * not granted. Hiding them would make the app's coverage invisible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onOpenType: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OnResume { viewModel.refresh() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenPermissions) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.permissions_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingView(Modifier.padding(padding))

            state.availability != Availability.Available -> MessageView(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.availability_missing_title),
                body = stringResource(R.string.availability_missing_body),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(modifier = Modifier.padding(padding)) {
                RecordRegistry.byCategory.forEach { (category, specs) ->
                    item(key = "header_${category.name}") { CategoryHeader(category) }
                    items(specs, key = { it.type.simpleName.orEmpty() }) { spec ->
                        TypeRow(
                            spec = spec,
                            status = state.statusOf(spec),
                            onClick = { onOpenType(spec.type.simpleName.orEmpty()) },
                        )
                    }
                }
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
private fun TypeRow(
    spec: RecordTypeSpec<*>,
    status: TypeStatus,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, tint, description) = when (status) {
            TypeStatus.HAS_DATA -> Triple(
                Icons.Default.CheckCircle,
                MaterialTheme.colorScheme.primary,
                R.string.status_has_data,
            )
            TypeStatus.NO_DATA -> Triple(
                Icons.Default.RemoveCircleOutline,
                MaterialTheme.colorScheme.onSurfaceVariant,
                R.string.status_no_data,
            )
            TypeStatus.NOT_GRANTED -> Triple(
                Icons.Default.Lock,
                MaterialTheme.colorScheme.onSurfaceVariant,
                R.string.status_not_granted,
            )
            TypeStatus.UNKNOWN -> Triple(
                Icons.Default.RemoveCircleOutline,
                MaterialTheme.colorScheme.onSurfaceVariant,
                R.string.status_unknown,
            )
        }

        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = stringResource(spec.displayNameRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
