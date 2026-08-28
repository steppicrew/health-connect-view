package de.steppicrew.healthconnectview.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient

/** Whether Health Connect can be used on this device. */
sealed interface Availability {
    data object Available : Availability
    data object NotInstalled : Availability
    data object UpdateRequired : Availability
}

/**
 * [HealthConnectClient.getOrCreate] throws when the provider is missing, so availability
 * must always be resolved before a client is constructed.
 */
fun resolveAvailability(context: Context): Availability =
    when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.UpdateRequired
        else -> Availability.NotInstalled
    }
