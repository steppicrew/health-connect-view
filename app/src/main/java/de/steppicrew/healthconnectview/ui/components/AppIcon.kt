package de.steppicrew.healthconnectview.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import de.steppicrew.healthconnectview.util.appIconFor
import de.steppicrew.healthconnectview.util.appLabelFor

/**
 * The writing app's icon, or null when there is none to draw.
 *
 * Null for an uninstalled app and for Health Connect's synthetic origin for the phone's own
 * sensors, which is not a real package. Callers branch on this to fall back to the app's
 * name, so the source is always identified one way or the other.
 *
 * Resolved once per package, since the package manager is a binder call and these sit in
 * lists that scroll.
 */
@Composable
fun rememberAppIcon(packageName: String): Drawable? {
    val context = LocalContext.current
    return remember(packageName) { context.appIconFor(packageName) }
}

/**
 * Draws an app's icon, with its name as the content description.
 *
 * The label is never dropped, only moved: an icon alone is unreadable to a screen reader, and
 * two fitness apps' marks are not reliably distinguishable at this size.
 */
@Composable
fun AppIcon(
    icon: Drawable,
    packageName: String,
    sizePx: Int,
    modifier: Modifier = Modifier,
) {
    val label = LocalContext.current.appLabelFor(packageName)
    Image(
        painter = BitmapPainter(remember(icon, sizePx) {
            icon.toBitmap(sizePx, sizePx).asImageBitmap()
        }),
        contentDescription = label,
        modifier = modifier,
    )
}
