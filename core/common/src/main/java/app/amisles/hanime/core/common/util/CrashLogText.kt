package app.amisles.hanime.core.common.util

internal object CrashLogText {

    private const val PREVIEW_MAX = 120

    fun previewOf(text: String): String =
        text.lineSequence()
            .firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?.trim()
            ?.take(PREVIEW_MAX)
            .orEmpty()
}
