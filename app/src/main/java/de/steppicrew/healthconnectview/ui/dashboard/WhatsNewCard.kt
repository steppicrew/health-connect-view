package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.settings.WhatsNewStore
import kotlinx.coroutines.launch

/**
 * What the versions with new features brought, once, above the tiles after an update -- the
 * owner's choice over a dialog, which would stand between someone and their numbers. Every
 * version since the last card put away, newest first: releases come often, and someone who
 * skipped one would otherwise never hear of its features (the owner, 09.10.2026).
 *
 * A release of fixes adds nothing and shows nothing. A release with features adds a
 * `whats_new_<versionCode>` string in every language, one point a line, and an entry in
 * [WHATS_NEW].
 */
@Composable
fun WhatsNewCard(modifier: Modifier = Modifier) {
    val store = WhatsNewStore(LocalContext.current)
    val scope = rememberCoroutineScope()
    var due by remember { mutableStateOf(emptyList<Int>()) }
    LaunchedEffect(Unit) {
        due = runCatching { store.due(WHATS_NEW.keys.toList()) }.getOrDefault(emptyList())
    }
    if (due.isEmpty()) return
    val points = due.sortedDescending().flatMap { version ->
        stringResource(WHATS_NEW.getValue(version)).lines().filter { it.isNotBlank() }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.whats_new_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 8.dp),
            )
            // One point a line in the text, each drawn beside its bullet so a wrapped line keeps
            // to the text rather than running back under the bullet.
            points.forEach { line ->
                Row(Modifier.padding(end = 8.dp)) {
                    Text(
                        text = BULLET,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.width(BULLET_WIDTH.dp),
                    )
                    Text(
                        text = line.removePrefix(BULLET).trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            TextButton(
                onClick = {
                    val latest = due.max()
                    due = emptyList()
                    scope.launch { runCatching { store.dismiss(latest) } }
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.whats_new_dismiss))
            }
        }
    }
}

/** Each versionCode with new features, and the text naming them; see [WhatsNewCard]. */
private val WHATS_NEW: Map<Int, Int> = mapOf(
    20 to R.string.whats_new_20,
    18 to R.string.whats_new_18,
)

private const val BULLET = "•"
private const val BULLET_WIDTH = 12
