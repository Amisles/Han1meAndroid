package arch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleGraphTest {

    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val featureModules = listOf(
        "feature-home",
        "feature-search",
        "feature-detail",
        "feature-download",
        "feature-profile",
        "feature-settings",
        "feature-comic"
    )

    private val projectDependency = Regex("""project\("(:[^"]+)"\)""")

    @Test
    fun `feature modules are declared in settings`() {
        val settings = File(root, "settings.gradle.kts").readText()
        val declared = Regex("""include\(":(feature-[^"]+)"\)""")
            .findAll(settings)
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(featureModules.toSet(), declared)
    }

    @Test
    fun `feature modules only depend on core and domain`() {
        val allowed = setOf(":core:common", ":core:ui", ":domain:model")
        val violations = featureModules.mapNotNull { module ->
            val buildFile = File(root, module + "/build.gradle.kts")
            val dependencies = projectDependency.findAll(buildFile.readText())
                .map { it.groupValues[1] }
                .filter { it !in allowed }
                .toList()
            if (dependencies.isEmpty()) null else module + " -> " + dependencies.joinToString()
        }
        assertTrue("feature 模块的生产依赖越界：\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
