package de.steppicrew.healthconnectview.export

/** How an export went, for the one-line message after it. Never carries any of the data. */
sealed interface ExportResult {
    data class Written(val rows: Int) : ExportResult
    data class Report(val readings: Int) : ExportResult
    data object Failed : ExportResult

    /** The window holds nothing for this file, so no dialog was opened and no file made. */
    data object Empty : ExportResult
}
