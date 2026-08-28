package de.steppicrew.healthconnectview.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Runs [onResume] whenever the screen comes back to the foreground.
 *
 * Permission state cannot be cached: the user grants access in Health Connect's own UI, and
 * can revoke it from system settings while this app is backgrounded. Re-reading on resume is
 * what keeps the displayed state honest.
 */
@Composable
fun OnResume(onResume: () -> Unit) {
    val currentOnResume by rememberUpdatedState(onResume)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
