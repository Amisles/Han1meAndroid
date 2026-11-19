package app.amisles.hanime.data.preferences

import android.content.Context
import app.amisles.hanime.domain.model.SearchFilterState
import app.amisles.hanime.domain.repository.SearchFilterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchFilterStore @Inject constructor(
    @ApplicationContext context: Context
) : SearchFilterRepository {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun load(): SearchFilterState = SearchFilterCodec.decode(prefs.getString(KEY, null))

    override fun save(state: SearchFilterState) {
        prefs.edit().putString(KEY, SearchFilterCodec.encode(state)).apply()
    }

    private companion object {
        const val NAME = "search_filters"
        const val KEY = "state"
    }
}

internal object SearchFilterCodec {

    private const val VERSION = "1"
    private const val FIELD_SEPARATOR = "\u001F"
    private const val TAG_SEPARATOR = "\u001E"

    fun encode(state: SearchFilterState): String = listOf(
        VERSION,
        state.genre.orEmpty(),
        state.sort.orEmpty(),
        if (state.broad) "1" else "0",
        state.date,
        state.duration,
        state.tags.joinToString(TAG_SEPARATOR)
    ).joinToString(FIELD_SEPARATOR)

    fun decode(raw: String?): SearchFilterState {
        if (raw.isNullOrBlank()) return SearchFilterState()
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size < 7 || parts[0] != VERSION) return SearchFilterState()
        return SearchFilterState(
            genre = parts[1].ifBlank { null },
            sort = parts[2].ifBlank { null },
            broad = parts[3] == "1",
            date = parts[4],
            duration = parts[5],
            tags = parts[6].split(TAG_SEPARATOR).filter { it.isNotBlank() }
        )
    }
}
