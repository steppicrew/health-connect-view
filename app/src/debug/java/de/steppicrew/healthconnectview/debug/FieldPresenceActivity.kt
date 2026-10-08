package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TimeRange
import de.steppicrew.healthconnectview.registry.RecordRegistry
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only. Reports which fields of each record type the apps on this device actually fill,
 * to find data Health Connect holds that the app never shows.
 *
 * Per type and field it logs how many records have the field filled and how many distinct
 * values it takes; for lists, their average length; for a route, which result class came
 * back. Record metadata is reported the same way, and the ids of a few routed workouts are
 * listed for the nav backdoor. It never logs a value, a timestamp, a
 * title or a note -- only counts.
 *
 * "Filled" means not null, not an empty list or string, and not 0 for a number: the
 * library's enum-like ints use 0 for "unknown", so a 0 is the field left unset.
 *
 * Keep it short: Health Connect refuses reads once the activity backgrounds, and this one
 * finishes as soon as it has logged (see CLAUDE.md, reads require the foreground).
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.FieldPresenceActivity [-e routedOn 2026-08-16]
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
                    if (spec.type == ExerciseSessionRecord::class) reportRouted(records)
                }
            intent.getStringExtra("routedOn")?.let { reportRoutedOn(repository, LocalDate.parse(it)) }
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

    /**
     * The ids of the five newest sessions with a readable route, so one can be opened through
     * the nav backdoor (`session/EXERCISE/<id>`). An id and a type code are no reading.
     */
    private fun reportRouted(records: List<Record>) {
        records.filterIsInstance<ExerciseSessionRecord>()
            .filter { it.exerciseRouteResult is ExerciseRouteResult.Data }
            .sortedByDescending { it.startTime }
            .take(5)
            .forEach { Log.i(TAG, "  routed id=${it.metadata.id} type=${it.exerciseType}") }
    }

    /** With `-e routedOn 2026-08-16`: the routed sessions of that day, older than the month above. */
    private suspend fun reportRoutedOn(repository: HealthRepository, day: LocalDate) {
        val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val records = runCatching {
            repository.read(ExerciseSessionRecord::class, TimeRangeFilter.between(start, start.plus(Duration.ofDays(1))))
        }.getOrDefault(emptyList())
        Log.i(TAG, "on $day:")
        reportRouted(records)
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
        // Per writer, how many distinct devices it names: can one source chip hide two phones
        // or two watches? Counts and package names only -- never a device's name.
        metadata.groupBy { it.dataOrigin.packageName }.forEach { (origin, ofOrigin) ->
            val named = ofOrigin.mapNotNull { it.device }.filter { !it.model.isNullOrBlank() || !it.manufacturer.isNullOrBlank() }
            val distinct = named.map { it.manufacturer.orEmpty() + "|" + it.model.orEmpty() }.distinct().size
            val kinds = ofOrigin.mapNotNull { it.device?.type }.distinct().sorted()
            Log.i(TAG, "  $name.source $origin records=${ofOrigin.size} named=${named.size} distinctDevices=$distinct kinds=$kinds")
        }
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
