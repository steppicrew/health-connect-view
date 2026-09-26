package de.steppicrew.healthconnectview.billing

/** Debug build: every feature unlocked, so premium ones are testable without a Play account. */
object AppEntitlements {
    val current: Entitlements = DebugEntitlements()
}
