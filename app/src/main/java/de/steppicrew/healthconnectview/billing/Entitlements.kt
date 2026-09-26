package de.steppicrew.healthconnectview.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Where the one-time Pro unlock stands for this user.
 *
 * [price] is Play's own formatted string for the user's country, or null while it is unknown --
 * Play unreachable, or the product not offered where the user is. Without it there is nothing
 * to buy, so the buy button hides rather than offering a purchase that cannot start.
 */
data class ProState(
    val owned: Boolean = false,
    /** Paid by a slow method (cash at a shop, bank transfer); Pro unlocks once Play confirms. */
    val pending: Boolean = false,
    val price: String? = null,
)

/**
 * Whether the user may use a given feature.
 *
 * One gate for the whole app rather than purchase checks scattered through the UI, so adding
 * a paid feature never means touching billing plumbing.
 */
interface Entitlements {
    val pro: StateFlow<ProState>

    val isPremium: Flow<Boolean> get() = pro.map { it.owned }

    fun has(feature: Feature): Flow<Boolean> = pro.map { !feature.isPremium || it.owned }

    /** Re-reads what the user owns; a purchase made or refunded elsewhere shows up here. */
    fun refresh()

    /** Starts Play's purchase sheet for Pro. While [ProState.price] is unknown it only retries loading it. */
    fun buy(activity: Activity)
}

/** Debug builds unlock everything, so premium features are testable without a Play account. */
class DebugEntitlements : Entitlements {
    override val pro: StateFlow<ProState> = MutableStateFlow(ProState(owned = true))

    override fun refresh() = Unit

    override fun buy(activity: Activity) = Unit
}
