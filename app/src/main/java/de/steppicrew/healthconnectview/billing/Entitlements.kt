package de.steppicrew.healthconnectview.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Whether the user may use a given feature.
 *
 * One gate for the whole app rather than purchase checks scattered through the UI, so adding
 * a paid feature never means touching billing plumbing.
 */
interface Entitlements {
    val isPremium: Flow<Boolean>
    fun has(feature: Feature): Flow<Boolean>
}

/**
 * Real entitlements come from Play Billing. Until products exist in the Play Console there is
 * nothing to own, so this reports no premium access; the app stays fully usable regardless.
 */
class BillingEntitlements : Entitlements {
    private val premium = MutableStateFlow(false)

    override val isPremium: Flow<Boolean> = premium

    override fun has(feature: Feature): Flow<Boolean> =
        premium.map { owned -> !feature.isPremium || owned }
}

/** Debug builds unlock everything, so premium features are testable without a Play account. */
class DebugEntitlements : Entitlements {
    private val always = MutableStateFlow(true)

    override val isPremium: Flow<Boolean> = always

    override fun has(feature: Feature): Flow<Boolean> = always
}
