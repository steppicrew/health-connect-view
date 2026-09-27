package de.steppicrew.healthconnectview.export

import android.app.Activity
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream
import java.io.IOException

/**
 * Shows a finished PDF in the system's print preview, where it can be read, printed or saved
 * with "Save as PDF". Returns false when the device has no print service to show it.
 *
 * The way to look at a report without saving it first. A viewer app would need a file to read,
 * and a file the user did not choose is exactly what the app promises never to write; the
 * preview takes the bytes from memory and the system's spooler does the rest.
 */
fun printPdf(activity: Activity, jobName: String, pdf: ByteArray): Boolean {
    val manager = activity.getSystemService(PrintManager::class.java) ?: return false
    return runCatching {
        manager.print(jobName, BytesAdapter(jobName, pdf), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }.isSuccess
}

/** Hands an already laid-out PDF to the print framework; the pages were fixed when it was drawn. */
private class BytesAdapter(private val name: String, private val pdf: ByteArray) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
        callback.onLayoutFinished(info, oldAttributes == null)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        try {
            // The framework owns and closes the descriptor; closing the stream would close it early.
            val out = FileOutputStream(destination.fileDescriptor)
            out.write(pdf)
            out.flush()
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.javaClass.simpleName)
        }
    }
}
