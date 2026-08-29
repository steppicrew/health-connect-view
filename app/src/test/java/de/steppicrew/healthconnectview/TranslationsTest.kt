package de.steppicrew.healthconnectview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the translation files. A locale that omits a key silently falls back to English,
 * which is easy to miss; one that adds a stale key is dead weight left behind by a rename.
 */
class TranslationsTest {

    private val resDir = File("src/main/res")

    private fun keysIn(file: File): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()

    private fun pluralKeysIn(file: File): Set<String> =
        Regex("""<plurals name="([^"]+)"""").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()

    private fun untranslatableKeysIn(file: File): Set<String> =
        Regex("""<string name="([^"]+)"\s+translatable="false"""").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `every locale defines only keys that exist in the default locale`() {
        val defaults = keysIn(File(resDir, "values/strings.xml"))

        localeFiles().forEach { file ->
            val stale = keysIn(file) - defaults
            assertTrue("${file.parentFile.name} defines unknown keys: $stale", stale.isEmpty())
        }
    }

    @Test
    fun `no locale translates strings marked untranslatable`() {
        val fixed = untranslatableKeysIn(File(resDir, "values/strings.xml"))

        localeFiles().forEach { file ->
            val overridden = keysIn(file).intersect(fixed)
            assertTrue(
                "${file.parentFile.name} translates fixed values (units): $overridden",
                overridden.isEmpty(),
            )
        }
    }

    /**
     * Reports how complete each locale is. Partial translations are allowed -- Android falls
     * back to English per string -- but a locale that has drifted far behind should be visible
     * rather than silently half-English.
     */
    @Test
    fun `locale coverage is reported`() {
        val defaults = keysIn(File(resDir, "values/strings.xml"))
        val translatable = defaults - untranslatableKeysIn(File(resDir, "values/strings.xml"))

        localeFiles().forEach { file ->
            val covered = keysIn(file).size
            val percent = covered * 100 / translatable.size
            println("${file.parentFile.name}: $covered/${translatable.size} ($percent%)")
        }
    }

    /**
     * A locale that was complete must stay complete.
     *
     * Coverage alone only prints, so a string added without its translation showed up as a
     * line of English on an otherwise German screen and nothing failed. Locales are allowed
     * to be partial -- Android falls back per string -- but one already at 100% is a locale
     * someone actually reads, and silently dropping it to 99% is the regression.
     */
    @Test
    fun `a fully translated locale stays fully translated`() {
        val defaults = File(resDir, "values/strings.xml")
        val translatable = keysIn(defaults) - untranslatableKeysIn(defaults)
        val defaultPlurals = pluralKeysIn(defaults)

        localeFiles().forEach { file ->
            val missing = translatable - keysIn(file)
            val missingPlurals = defaultPlurals - pluralKeysIn(file)
            // Judged against this locale's own reach: a locale that never covered a section
            // is partial by choice, while one missing only the newest keys has fallen behind.
            val covered = translatable.size - missing.size
            val wasComplete = covered >= translatable.size * COMPLETE_THRESHOLD / 100

            if (wasComplete) {
                assertTrue(
                    "${file.parentFile.name} is otherwise complete but omits: " +
                        (missing + missingPlurals),
                    missing.isEmpty() && missingPlurals.isEmpty(),
                )
            }
        }
    }

    /** Above this a locale counts as maintained, so a gap in it is an oversight. */
    private val COMPLETE_THRESHOLD = 95

    private fun localeFiles(): List<File> =
        resDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-") && it.name != "values-night" }
            ?.mapNotNull { File(it, "strings.xml").takeIf(File::exists) }
            ?: emptyList()
}
