package de.steppicrew.healthconnectview.billing

/**
 * Release build: what Play Billing says. Until a product exists in the Play Console that is
 * "nothing owned", so premium features show as locked for everyone.
 */
object AppEntitlements {
    val current: Entitlements = BillingEntitlements()
}
