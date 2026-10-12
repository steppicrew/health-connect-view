package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.SharedReads
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedReadsTest {
    @Test
    fun `everyone asking the same thing at once gets one read`() = runBlocking {
        val shared = SharedReads()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        val answers = List(3) {
            async {
                shared.apps.get(listOf("apps", 1)) {
                    reads++
                    release.await()
                    setOf("a")
                }
            }
        }
        yield()
        release.complete(Unit)

        assertEquals(List(3) { setOf("a") }, answers.awaitAll())
        assertEquals(1, reads)
        assertEquals(1, shared.issued)
        assertEquals(2, shared.shared)
    }

    @Test
    fun `different requests are read separately`() = runBlocking {
        val shared = SharedReads()
        shared.apps.get(listOf("apps", 1)) { setOf("a") }
        shared.apps.get(listOf("apps", 2)) { setOf("b") }

        assertEquals(2, shared.issued)
        assertEquals(0, shared.shared)
    }

    @Test
    fun `a failed read is not kept`() = runBlocking {
        val shared = SharedReads()
        val failed = runCatching { shared.apps.get("key") { error("rate limited") } }

        assertTrue(failed.isFailure)
        assertEquals(setOf("a"), shared.apps.get("key") { setOf("a") })
        assertEquals(2, shared.issued)
    }

    @Test
    fun `a null answer is shared, not read again`() = runBlocking {
        val shared = SharedReads()
        var reads = 0
        repeat(2) { shared.oldest.get("key") { reads++; null } }

        assertEquals(1, reads)
    }

    @Test
    fun `an asker whose reader was cancelled reads for itself`() = runBlocking {
        val shared = SharedReads()
        val started = CompletableDeferred<Unit>()
        val reader = async {
            shared.apps.get("key") {
                started.complete(Unit)
                CompletableDeferred<Set<String>>().await()
            }
        }
        started.await()
        val waiter = async { shared.apps.get("key") { setOf("own") } }
        yield()
        reader.cancel()

        assertEquals(setOf("own"), waiter.await())
    }
}
