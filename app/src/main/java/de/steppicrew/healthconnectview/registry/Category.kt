package de.steppicrew.healthconnectview.registry

import androidx.annotation.StringRes
import de.steppicrew.healthconnectview.R

/** Top-level grouping shown as headers in the catalog. */
enum class Category(@param:StringRes val labelRes: Int) {
    ACTIVITY(R.string.category_activity),
    BODY(R.string.category_body),
    VITALS(R.string.category_vitals),
    NUTRITION(R.string.category_nutrition),
    SLEEP(R.string.category_sleep),
    CYCLE(R.string.category_cycle),
}
