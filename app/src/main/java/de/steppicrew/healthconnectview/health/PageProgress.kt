package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.Record
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant

/**
 * How far an oldest-first paged read has got through its range, from the time of the last
 * record read, reported as 0..1.
 *
 * A year of respiratory rate is hundreds of thousands of readings and a minute or more of
 * paging; without this the progress bar sat at 0 for all of it and then jumped to the end. The
 * record count is unknown until the read finishes, but the range is known from the start, and
 * readings arrive in time order, so the time reached is an honest measure of the work done.
 */
class PageProgress<T : Record>(
    private val timeOf: (T) -> Instant,
    private val report: (Float) -> Unit,
) {
    /** Records read so far: for a read of the whole window, how many the window holds. */
    var records: Int = 0
        private set

    fun afterPage(range: TimeRangeFilter, page: List<T>) {
        records += page.size
        val last = page.lastOrNull() ?: return
        val fraction = fractionOf(range, timeOf(last)) ?: return
        report(fraction)
    }

    companion object {
        /** Where [time] falls in [range], or null for a range given in local time or empty. */
        fun fractionOf(range: TimeRangeFilter, time: Instant): Float? {
            val start = range.startTime ?: return null
            val end = range.endTime ?: return null
            val span = Duration.between(start, end).toMillis().takeIf { it > 0 } ?: return null
            return (Duration.between(start, time).toMillis().toFloat() / span).coerceIn(0f, 1f)
        }
    }
}
