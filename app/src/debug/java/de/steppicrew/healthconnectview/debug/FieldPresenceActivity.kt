package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.Record
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TimeRange
import de.steppicrew.healthconnectview.registry.RecordRegistry
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Debug-only. Reports which fields of each record type the apps on this device actually fill,
 * to find data Health Connect holds that the app never shows.
 *
 * Per type and field it logs how many records have the field filled and how many distinct
 * values it takes; for lists, their average length; for a route, which result class came
 * back. Record metadata is reported the same way. It never logs a value, a timestamp, a
 * title or a note -- only counts.
 *
 * "Filled" means not null, not an empty list or string, and not 0 for a number: the
 * library's enum-like ints use 0 for "unknown", so a 0 is the field left unset.
 *
 * Keep it short: Health Connect refuses reads once the activity backgrounds, and this one
 * finishes as soon as it has logged (see CLAUDE.md, reads require the foreground).
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.FieldPresenceActivity
 */
class FieldPresenceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)

        lifecycleScope.launch {
            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            RecordRegistry.all
                .filter { it.permission in granted }
                .forEach { spec ->
                    val name = spec.type.simpleName ?: return@forEach
                    val records = runCatching { repository.read(spec.type, TimeRange.MONTH.filter()) }
                        .getOrDefault(emptyList())
                    if (records.isEmpty()) return@forEach
                    Log.i(TAG, "$name records=${records.size}")
                    report(name, records)
                    reportMetadata(name, records)
                }
            Log.i(TAG, "done")
            finish()
        }
    }

    private fun report(name: String, records: List<Record>) {
        getters(records.first().javaClass).forEach { getter ->
            val values = records.map { runCatching { getter.invoke(it) }.getOrNull() }
            val field = getter.name.removePrefix("get").removePrefix("is")
            val filled = values.count(::isFilled)
            val lists = values.filterIsInstance<Collection<*>>()
            val detail = when {
                lists.isNotEmpty() -> "avgLength=${"%.1f".format(lists.map { it.size }.average())}"
                field == "ExerciseRouteResult" ->
                    values.groupingBy { it?.javaClass?.simpleName ?: "null" }.eachCount().toString()
                else -> "distinct=${values.filter(::isFilled).distinct().size}"
            }
            Log.i(TAG, "  $name.$field filled=$filled/${records.size} $detail")
        }
    }

    private fun reportMetadata(name: String, records: List<Record>) {
        val metadata = records.map { it.metadata }
        val methods = metadata.groupingBy { it.recordingMethod }.eachCount()
        val devices = metadata.count { it.device != null }
        val deviceTypes = metadata.mapNotNull { it.device?.type }.groupingBy { it }.eachCount()
        val clientIds = metadata.count { !it.clientRecordId.isNullOrEmpty() }
        Log.i(
            TAG,
            "  $name.metadata recordingMethod=$methods device=$devices/${records.size} " +
                "deviceType=$deviceTypes clientRecordId=$clientIds",
        )
    }

    /** The record's own data getters: not its times, offsets, metadata or Java plumbing. */
    private fun getters(type: Class<*>): List<Method> = type.methods
        .filter { Modifier.isPublic(it.modifiers) && it.parameterCount == 0 }
        .filter { (it.name.startsWith("get") || it.name.startsWith("is")) && it.name !in SKIPPED }
        .filterNot { it.name.endsWith("ZoneOffset") || it.name.endsWith("Time") }
        .sortedBy { it.name }

    private fun isFilled(value: Any?): Boolean = when (value) {
        null -> false
        is Collection<*> -> value.isNotEmpty()
        is CharSequence -> value.isNotBlank()
        is Number -> value.toDouble() != 0.0
        is Boolean -> value
        else -> !value.javaClass.simpleName.contains("NoData")
    }

    private companion object {
        const val TAG = "FieldPresence"
        val SKIPPED = setOf("getClass", "getMetadata")
    }
}
