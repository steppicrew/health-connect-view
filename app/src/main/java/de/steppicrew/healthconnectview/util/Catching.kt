package de.steppicrew.healthconnectview.util

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] that lets a coroutine's cancellation through.
 *
 * `runCatching` catches every `Throwable`, `CancellationException` included, so a load that was
 * superseded -- a day stepped on, a screen left -- turned its cancellation into an ordinary
 * failure, read on and could still publish its stale result. Three places had already been
 * rewritten by hand for this (the cycle screen, the catalog probe, the training nights); this is
 * the one rule for all of them. Outside a coroutine nothing throws a `CancellationException`, so
 * it does no harm there either.
 */
inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
