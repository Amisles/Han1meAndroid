package app.amisles.hanime.domain.repository

/** 云播放列表同步所需的本地状态：CSRF 令牌与迁移标记。 */
interface PlaylistSettings {
    var playlistCsrfToken: String
    var playlistMigrationDone: Boolean
}
