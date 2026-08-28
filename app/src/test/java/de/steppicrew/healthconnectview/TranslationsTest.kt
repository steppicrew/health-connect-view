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

    private fun localeFiles(): List<File> =
        resDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-") && it.name != "values-night" }
            ?.mapNotNull { File(it, "strings.xml").takeIf(File::exists) }
            ?: emptyList()
}
