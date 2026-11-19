package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.CreatedPlaylist
import app.amisles.hanime.domain.model.PlaylistSaveResult
import app.amisles.hanime.domain.result.AppException
import app.amisles.hanime.domain.result.DomainError
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
            .getOrElse { throw AppException(DomainError.PLAYLIST_RESPONSE_INVALID, it) }
        val saveBtn = obj.optString("saveBtn")
        return PlaylistSaveResult(
            saved = saveBtn.contains("playlist_add_check"),
            csrfToken = obj.optString("csrf_token").trim()
        )
    }

    fun parseCreatedPlaylist(json: String): CreatedPlaylist {
        val obj = runCatching { JSONObject(json) }
            .getOrElse { throw AppException(DomainError.PLAYLIST_RESPONSE_INVALID, it) }
        val checkbox = obj.optString("checkbox")
        val input = Jsoup.parseBodyFragment(checkbox, "").selectFirst("input.playlist-checkbox")
            ?: throw AppException(DomainError.PLAYLIST_CREATE_FAILED)
        val option = saveOptionsParser.optionOf(input)
            ?: throw AppException(DomainError.PLAYLIST_RESPONSE_INVALID)
        return CreatedPlaylist(
            remoteId = option.target,
            title = option.title,
            csrfToken = obj.optString("csrf_token").trim()
        )
    }
}