package de.steppicrew.healthconnectview.registry

import androidx.annotation.StringRes
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import de.steppicrew.healthconnectview.R

/**
 * How a record was made, from the metadata every record carries and the app never read: by
 * hand, recorded during an activity, or measured in the background -- and by what kind of
 * device.
 *
 * On the phone a month of steps came from a watch and a phone at once, which is exactly the
 * overlap the source picker explains; a row saying "Uhr" or "Telefon" shows it where it
 * happens. A manual entry among measured values is the other thing a list should not hide.
 */
enum class RecordingMethod(
    /** Health Connect's `Metadata.RECORDING_METHOD_*` code. */
    val code: Int,
    @param:StringRes val labelRes: Int,
    /** Named in a list row. Automatic is how nearly everything arrives, so saying it is noise. */
    val shownInList: Boolean,
    /** A stable word for the CSV, the same in every language. */
    val csv: String,
) {
    MANUAL(Metadata.RECORDING_METHOD_MANUAL_ENTRY, R.string.recording_manual, true, "manual"),
    ACTIVE(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED, R.string.recording_active, true, "active"),
    AUTOMATIC(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED, R.string.recording_automatic, false, "automatic"),
    UNKNOWN(Metadata.RECORDING_METHOD_UNKNOWN, R.string.recording_unknown, false, ""),
    ;

    companion object {
        fun of(code: Int): RecordingMethod = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** The kind of device a record came from, as its writer declared it. */
enum class DeviceKind(
    /** Health Connect's `Device.TYPE_*` code. */
    val code: Int,
    @param:StringRes val labelRes: Int,
    val csv: String,
) {
    WATCH(Device.TYPE_WATCH, R.string.device_watch, "watch"),
    PHONE(Device.TYPE_PHONE, R.string.device_phone, "phone"),
    SCALE(Device.TYPE_SCALE, R.string.device_scale, "scale"),
    RING(Device.TYPE_RING, R.string.device_ring, "ring"),
    HEAD_MOUNTED(Device.TYPE_HEAD_MOUNTED, R.string.device_head_mounted, "head_mounted"),
    FITNESS_BAND(Device.TYPE_FITNESS_BAND, R.string.device_fitness_band, "fitness_band"),
    CHEST_STRAP(Device.TYPE_CHEST_STRAP, R.string.device_chest_strap, "chest_strap"),
    SMART_DISPLAY(Device.TYPE_SMART_DISPLAY, R.string.device_smart_display, "smart_display"),
    ;

    companion object {
        /** Null for no device or an unknown kind: nothing is said rather than "unknown". */
        fun of(device: Device?): DeviceKind? = device?.let { d -> entries.firstOrNull { it.code == d.type } }
    }
}

/**
 * The device's own name as the writer stored it, "manufacturer model", or null. A brand here
 * is the user's own data -- the device the reading came from -- like the source app's name.
 */
fun deviceName(device: Device?): String? = deviceName(device?.manufacturer, device?.model)

/** The naming rule on its own, so it can be tested without building a [Device]. */
internal fun deviceName(manufacturer: String?, model: String?): String? {
    val maker = manufacturer?.trim().orEmpty()
    val name = model?.trim().orEmpty()
    return when {
        maker.isEmpty() -> name.ifEmpty { null }
        name.isEmpty() -> maker
        // Many writers put the maker in the model too; "Garmin Garmin Forerunner" says it twice.
        name.startsWith(maker, ignoreCase = true) -> name
        else -> "$maker $name"
    }
}
