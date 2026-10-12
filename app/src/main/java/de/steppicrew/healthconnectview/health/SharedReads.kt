package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.Record
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * One answer per request for everyone asking it during one load: the first to ask reads, and
 * every later or concurrent asker awaits that same read rather than issuing its own.
 *
 * Tiles, their charts, their goals and their streaks each read what they need, and several
 * need the same thing -- a steps ring and a steps chart both replayed the adaptive goal from
 * the same year of daily totals, and the ring's streak read it a third time (12.10.2026).
 * Sharing the answers keeps each reader self-contained and still reads once.
 *
 * Lives for one load only (see [HealthRepository.sharing]), so a later load reads afresh and
 * no record outlives the screen that asked for it. A failed read is not kept: whoever asks
 * next tries again.
 */
class SharedReads {
    private val issuedCount = AtomicInteger()
    private val sharedCount = AtomicInteger()

    /** Reads sent to Health Connect, and requests answered by one already sent. */
    val issued: Int get() = issuedCount.get()
    val shared: Int get() = sharedCount.get()

    internal val records = Answers<List<Record>>()
    internal val oldest = Answers<Record?>()
    internal val presence = Answers<Boolean>()
    internal val periods = Answers<List<AggregationResultGroupedByPeriod>>()
    internal val durations = Answers<List<AggregationResultGroupedByDuration>>()
    internal val totals = Answers<Map<AggregateMetric<*>, Double>>()
    internal val apps = Answers<Set<String>>()

    /** The answers of one kind, keyed by everything their request depends on. */
    internal inner class Answers<V> {
        private val answers = ConcurrentHashMap<Any, CompletableDeferred<V>>()

        suspend fun get(key: Any, read: suspend () -> V): V {
            while (true) {
                val mine = CompletableDeferred<V>()
                val earlier = answers.putIfAbsent(key, mine)
                if (earlier == null) {
                    issuedCount.incrementAndGet()
                    return try {
                        read().also { mine.complete(it) }
                    } catch (e: Throwable) {
                        answers.remove(key, mine)
                        mine.completeExceptionally(e)
                        throw e
                    }
                }
                try {
                    return earlier.await().also { sharedCount.incrementAndGet() }
                } catch (e: CancellationException) {
                    // The reader was cancelled, not this asker: unless this one is cancelled
                    // too, read it here instead.
                    currentCoroutineContext().ensureActive()
                    answers.remove(key, earlier)
                }
            }
        }
    }
}
