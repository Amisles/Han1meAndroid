package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.SearchFilterState

interface SearchFilterRepository {

    fun load(): SearchFilterState

    fun save(state: SearchFilterState)
}
