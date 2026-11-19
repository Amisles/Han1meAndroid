package arch

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureRulesTest {

    private val files = Konsist.scopeFromProduction().files

    private val featureModules = listOf(
        "feature-home",
        "feature-search",
        "feature-detail",
        "feature-download",
        "feature-profile",
        "feature-settings",
        "feature-comic"
    )

    @Test
    fun `production scope covers the project`() {
        assertTrue("Konsist 没有读到生产代码，规则会静默通过", files.size > 100)
    }

    @Test
    fun `feature modules never depend on each other`() {
        val violations = violations { file, module, import ->
            if (!module.startsWith("feature-")) return@violations false
            val ownPackage = "app.amisles.hanime.feature." + module.removePrefix("feature-") + "."
            import.startsWith("app.amisles.hanime.feature.") && !import.startsWith(ownPackage)
        }

        assertTrue("feature 模块之间不允许互相依赖：\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `feature modules never touch network service or parsers`() {
        val violations = violations { _, module, import ->
            if (!module.startsWith("feature-")) return@violations false
            import.startsWith("app.amisles.hanime.data.remote.NetworkService") ||
                import.startsWith("app.amisles.hanime.data.parser.")
        }

        assertTrue(
            "feature 模块只能经过仓储访问网络与解析：\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `domain models stay free of android imports`() {
        val violations = violations { _, module, import ->
            module == "domain/model" && import.startsWith("android.")
        }

        assertTrue("domain:model 不允许依赖 Android：\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    private fun violations(isViolation: (file: String, module: String, import: String) -> Boolean): List<String> =
        files.flatMap { file ->
            val module = moduleOf(file.path)
            file.imports
                .map { it.name }
                .filter { import -> isViolation(relative(file.path), module, import) }
                .map { import -> relative(file.path) + " -> " + import }
        }

    private fun moduleOf(path: String): String {
        val normalized = path.replace('\\', '/')
        val prefix = normalized.substringBefore("/src/")
        return (featureModules + listOf("core/common", "core/ui", "domain/model", "data", "app"))
            .filter { prefix.endsWith("/" + it) }
            .maxByOrNull { it.length }
            ?: prefix.substringAfterLast('/')
    }

    private fun relative(path: String): String {
        val normalized = path.replace('\\', '/')
        return normalized.substringAfter("/src/main/java/", normalized)
    }
}
