package de.steppicrew.healthconnectview.export

import android.net.Uri

/**
 * How an export went, for the one-line message after it. Never carries any of the data -- only
 * where the user saved it, so the message can offer to open that file.
 */
sealed interface ExportResult {
    data class Written(val rows: Int, val uri: Uri, val mimeType: String) : ExportResult
    data class Report(val readings: Int, val uri: Uri, val mimeType: String) : ExportResult
    data object Failed : ExportResult

    /** The window holds nothing for this file, so no dialog was opened and no file made. */
    data object Empty : ExportResult

    /** No app on the device opens this kind of file, or none can print. */
    data object NoViewer : ExportResult
}
