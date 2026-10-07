package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One piece of information with everything that explains it -- a title, its numbers, the rule
 * behind its "i" -- outlined as a unit.
 *
 * Stacked unboxed under a chart, a streak's rule, a trend's averages and a record's date ran
 * into each other and into the chart's own notes, and which line belonged to what had to be
 * worked out. The outline answers it; what stays outside, right under the chart, is the
 * chart's.
 */
@Composable
fun InfoGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), content = content)
    }
}
