package de.steppicrew.healthconnectview.export

/** How an export went, for the one-line message after it. Never carries any of the data. */
sealed interface ExportResult {
    data class Written(val rows: Int) : ExportResult
    data object Failed : ExportResult
}
