package de.steppicrew.healthconnectview.health

import java.time.LocalDateTime
import java.time.Period

/**
 * One request of a long bucketed aggregate: asked [from]-[to], kept from [keepFrom].
 *
 * [from] lies one bucket before [keepFrom] for every piece but the first, and that bucket is
 * dropped. Health Connect computes the first bucket of a request differently from the same
 * bucket inside a longer one: split at the 7th of each month, a year of daily distance differed
 * on exactly the eleven 7ths, by up to 30 % (owner's phone, 07.11.2026). With the extra bucket
 * every kept bucket is an inner one, as in the single request.
 */
data class AggregationPiece(val from: LocalDateTime, val keepFrom: LocalDateTime, val to: LocalDateTime)

/**
 * How to ask for [start]-[end] in [bucket]s as about a month per request, or null to ask in one.
 *
 * A year of daily distance as one aggregateGroupByPeriod took 19-23 s on the owner's phone, as
 * twelve monthly requests 5.4 s, and every other call to Health Connect waited behind the single
 * request -- it was most of a 30 s dashboard load (07.11.2026). Steps, with fewer records, took
 * about a second either way.
 *
 * Only for buckets of whole days or weeks: a month added to the 31st lands on a different day
 * than a month added to the 1st, so pieces of month buckets could shift the bucket edges.
 */
fun aggregationPieces(start: LocalDateTime, end: LocalDateTime, bucket: Period): List<AggregationPiece>? {
    if (bucket.years != 0 || bucket.months != 0 || bucket.days <= 0) return null
    val perPiece = maxOf(1, PIECE_DAYS / bucket.days)
    val step = Period.ofDays(bucket.days * perPiece)
    if (!start.plus(step).plus(step).isBefore(end)) return null
    val pieces = mutableListOf<AggregationPiece>()
    var keepFrom = start
    while (keepFrom.isBefore(end)) {
        val to = minOf(keepFrom.plus(step), end)
        val from = if (pieces.isEmpty()) keepFrom else keepFrom.minus(bucket)
        pieces += AggregationPiece(from, keepFrom, to)
        keepFrom = to
    }
    return pieces
}

/** About a month: short enough that another call waits less than half a second. */
private const val PIECE_DAYS = 31
