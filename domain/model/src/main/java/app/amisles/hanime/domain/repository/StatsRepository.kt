package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.WatchStats

interface StatsRepository {
    suspend fun loadWatchStats(): WatchStats
}
