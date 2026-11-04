package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.CreatedPlaylist
import app.amisles.hanime.domain.model.PlaylistSaveResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.jsoup.Jsoup

@Singleton
class PlaylistSaveResponseParser @Inject constructor(
    private val saveOptionsParser: SaveOptionsParser
) {

    fun parseSave(json: String): PlaylistSaveResult {
        val obj = runCatching { JSONObject(json) }
            .getOrElse { throw IOException("保存结果不是合法 JSON") }
        val saveBtn = obj.optString("saveBtn")
        return PlaylistSaveResult(
            saved = saveBtn.contains("playlist_add_check"),
            csrfToken = obj.optString("csrf_token").trim()
        )
    }

    fun parseCreatedPlaylist(json: String): CreatedPlaylist {
        val obj = runCatching { JSONObject(json) }
            .getOrElse { throw IOException("创建清单返回不是合法 JSON") }
        val checkbox = obj.optString("checkbox")
        val input = Jsoup.parseBodyFragment(checkbox, "").selectFirst("input.playlist-checkbox")
            ?: throw IOException("创建清单未返回新清单")
        val option = saveOptionsParser.optionOf(input)
            ?: throw IOException("创建清单返回的清单 id 无效")
        return CreatedPlaylist(
            remoteId = option.target,
            title = option.title,
            csrfToken = obj.optString("csrf_token").trim()
        )
    }
}