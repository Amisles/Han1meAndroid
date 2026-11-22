package app.amisles.hanime.domain.preferences

/** 备份读取/恢复界面配置的端口；实现留在应用层，避免 data 反向依赖 core:ui。 */
interface UiConfigBackup {
    fun read(): String
    fun apply(raw: String): Boolean
}
