package de.steppicrew.healthconnectview.ui.dashboard

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import de.steppicrew.healthconnectview.dashboard.SourceStore
import de.steppicrew.healthconnectview.export.ExportPeriod
import de.steppicrew.healthconnectview.export.ExportResult
import de.steppicrew.healthconnectview.export.Exporter
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.recordsIn
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.ui.components.ExportKind
import de.steppicrew.healthconnectview.util.appLabelFor
import de.steppicrew.healthconnectview.util.catching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.reflect.KClass

/**
 * The detail screen's exports: a type's records or daily totals as CSV, its report, and the
 * combined report for a doctor, saved to a file the user chose or rendered for the print
 * preview. Each call names the type and the source filter on screen, and the repository the
 * view model reads through at that moment, so this holds nothing of the screen's own.
 */
internal class TileExports(private val application: Application, private val sourceStore: SourceStore) {

    /**
     * Whether [kind] over [period] would hold any data. Asked before the save dialog opens, so
     * an empty period no longer leaves a file of headers. A failed check says yes: the write
     * reports its own failure.
     */
    suspend fun hasData(
        repository: HealthRepository,
        spec: RecordTypeSpec<*>,
        source: String?,
        kind: ExportKind,
        period: ExportPeriod,
        combined: Boolean,
    ): Boolean {
        val origins = originsOf(source)
        val exporter = Exporter(application, repository)
        return catching {
            if (combined) {
                val sources = reportSources(repository, spec, source, period)
                return@catching Exporter.REPORT_TYPES.any { type ->
                    val each = RecordRegistry.specOrNull(type.simpleName.orEmpty()) ?: return@any false
                    exporter.hasReportData(each, period.first, period.last, originsOf(sources[type]))
                }
            }
            when (kind) {
                ExportKind.RECORDS -> exporter.hasRecords(spec, period.start(), period.end(), origins)
                ExportKind.DAILY -> exporter.hasDailyTotals(spec, period.first, period.last.plusDays(1), origins)
                ExportKind.REPORT, ExportKind.PRINT -> exporter.hasReportData(spec, period.first, period.last, origins)
            }
        }.getOrDefault(true)
    }

    /**
     * Writes [spec], filtered to [source], over [period] to [uri]. A failed export removes the
     * file it started, so a half-written CSV is not left looking like a complete one.
     */
    suspend fun write(
        repository: HealthRepository,
        spec: RecordTypeSpec<*>,
        source: String?,
        kind: ExportKind,
        period: ExportPeriod,
        combined: Boolean,
        uri: Uri,
    ): ExportResult {
        val origins = originsOf(source)
        val resolver = application.contentResolver
        val exporter = Exporter(application, repository)
        val result = catching {
            withContext(Dispatchers.IO) {
                requireNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }.use { out ->
                    when (kind) {
                        ExportKind.RECORDS -> ExportResult.Written(
                            exporter.writeRecords(spec, period.start(), period.end(), origins, out),
                            uri,
                            kind.mimeType,
                        )
                        ExportKind.DAILY -> ExportResult.Written(
                            exporter.writeDailyTotals(spec, period.first, period.last.plusDays(1), origins, out),
                            uri,
                            kind.mimeType,
                        )
                        // Printing never reaches a file; see render.
                        ExportKind.PRINT -> error("print is not saved")
                        ExportKind.REPORT -> if (combined) {
                            val sources = reportSources(repository, spec, source, period)
                            ExportResult.Report(
                                exporter.writeDoctorReport(period.first, period.last, sources, labelsOf(sources), out),
                                uri,
                                kind.mimeType,
                            )
                        } else ExportResult.Report(
                            exporter.writeReport(spec, period.first, period.last, origins, source?.let { application.appLabelFor(it) }, out),
                            uri,
                            kind.mimeType,
                        )
                    }
                }
            }
        }
        result.onFailure {
            Log.w(TAG, "export failed: ${it.javaClass.simpleName}")
            catching { DocumentsContract.deleteDocument(resolver, uri) }
        }
        return result.getOrDefault(ExportResult.Failed)
    }

    /**
     * The report for [period] as PDF bytes in memory, for the print preview; null if it failed.
     * Read while the app is in front: Health Connect refuses reads once the preview covers it.
     * Held only until the preview has taken it -- never written anywhere by the app.
     */
    suspend fun render(
        repository: HealthRepository,
        spec: RecordTypeSpec<*>,
        source: String?,
        period: ExportPeriod,
        combined: Boolean,
    ): ByteArray? = catching {
        withContext(Dispatchers.IO) {
            ByteArrayOutputStream().also { out ->
                val exporter = Exporter(application, repository)
                if (combined) {
                    val sources = reportSources(repository, spec, source, period)
                    exporter.writeDoctorReport(period.first, period.last, sources, labelsOf(sources), out)
                } else {
                    exporter.writeReport(spec, period.first, period.last, originsOf(source), source?.let { application.appLabelFor(it) }, out)
                }
            }.toByteArray()
        }
    }.onFailure { Log.w(TAG, "report failed: ${it.javaClass.simpleName}") }.getOrNull()

    /**
     * The source of each report type for the combined report: [source] for the screen's own
     * type, and for the others what their screens would open on -- the choice made there, else
     * the preferred app where it wrote that type in [period].
     */
    private suspend fun reportSources(
        repository: HealthRepository,
        own: RecordTypeSpec<*>,
        source: String?,
        period: ExportPeriod,
    ): Map<KClass<out Record>, String?> {
        val selections = catching { sourceStore.selections.first() }.getOrDefault(emptyMap())
        val preferred = catching { sourceStore.preferred.first() }.getOrNull()
        return Exporter.REPORT_TYPES.associateWith { type ->
            if (type == own.type) return@associateWith source
            val name = type.simpleName.orEmpty()
            val spec = RecordRegistry.specOrNull(name) ?: return@associateWith null
            val writers = if (preferred != null && selections[name] == null) {
                catching { repository.recordsIn(spec, period.start(), period.end()).map { spec.originOf(it) }.toSet() }
                    .getOrDefault(emptySet())
            } else {
                emptySet()
            }
            sourceStore.effective(name, selections, preferred, writers)
        }
    }

    private fun labelsOf(sources: Map<KClass<out Record>, String?>): Map<KClass<out Record>, String?> =
        sources.mapValues { (_, source) -> source?.let { application.appLabelFor(it) } }

    private fun originsOf(source: String?): Set<DataOrigin> = source?.let { setOf(DataOrigin(it)) } ?: emptySet()

    private fun ExportPeriod.start(): Instant = first.atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()

    private fun ExportPeriod.end(): Instant = last.plusDays(1).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()
}

/** The same log tag as the detail screen it exports for. */
private const val TAG = "TileDetail"
