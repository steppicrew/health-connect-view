package de.steppicrew.healthconnectview.export

import de.steppicrew.healthconnectview.health.RoutePoint
import java.io.Writer
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A route as GPX 1.1, the track format nearly every map and training app opens -- Google Earth
 * included, Google Maps not (it opens no track format at all). One track, one segment,
 * position, elevation where recorded, and time per point.
 *
 * Numbers are written with a dot whatever the locale: GPX is XML with fixed number syntax, and
 * "52,5" would not parse. [name] is the session's title or activity and is escaped.
 */
object Gpx {
    fun write(points: List<RoutePoint>, name: String, out: Writer) {
        out.write("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
        out.write(
            """<gpx version="1.1" creator="Health Connect View" xmlns="http://www.topografix.com/GPX/1/1">""" + "\n",
        )
        out.write("  <trk>\n    <name>${escape(name)}</name>\n    <trkseg>\n")
        points.forEach { p ->
            out.write("""      <trkpt lat="${coordinate(p.latitude)}" lon="${coordinate(p.longitude)}">""")
            p.altitude?.let { out.write("<ele>${String.format(Locale.ROOT, "%.1f", it)}</ele>") }
            out.write("<time>${DateTimeFormatter.ISO_INSTANT.format(p.time)}</time></trkpt>\n")
        }
        out.write("    </trkseg>\n  </trk>\n</gpx>\n")
        out.flush()
    }

    /** Seven decimals: about a centimetre, beyond any recorded position's accuracy. */
    private fun coordinate(value: Double) = String.format(Locale.ROOT, "%.7f", value)

    private fun escape(text: String) = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
