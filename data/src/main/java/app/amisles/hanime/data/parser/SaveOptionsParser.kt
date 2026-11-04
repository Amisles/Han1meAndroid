package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.PlaylistSaveOption
import app.amisles.hanime.domain.model.VideoSaveOptions
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveOptionsParser @Inject constructor() {

    fun parse(doc: Document, fallbackCsrfToken: String = ""): VideoSaveOptions {
        val form = doc.selectFirst("#video-save-form") ?: return VideoSaveOptions()
        val videoId = form.selectFirst("input[name=playlist-video-id]")?.attr("value")?.trim().orEmpty()
        val token = form.selectFirst("input[name=_token]")?.attr("value")?.trim().orEmpty()
            .ifEmpty { fallbackCsrfToken }
        val options = form.select("input.playlist-checkbox").mapNotNull { input ->
            optionOf(input)
        }
        return VideoSaveOptions(videoId = videoId, csrfToken = token, options = options)
    }

    fun optionOf(input: Element): PlaylistSaveOption? {
        val target = input.id().trim()
        if (target.isEmpty()) return null
        val label = input.parent() ?: return null
        val title = label.selectFirst("span")?.text()?.trim().orEmpty()
        return PlaylistSaveOption(
            target = target,
            title = title.ifEmpty { target },
            isPrivate = isPrivateIcon(label),
            selected = input.hasAttr("checked")
        )
    }

    fun isPrivateIcon(label: Element): Boolean =
        label.selectFirst("img")?.attr("src").orEmpty().contains("private", ignoreCase = true)
}