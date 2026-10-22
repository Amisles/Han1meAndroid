package app.amisles.hanime.domain.model

/**
 * 下载存储面板的展示数据。
 *
 * [orphanPartmapCount] 为磁盘上存在、却已无任务引用的位图文件数量：
 * 任务被外部删除或数据库被清空后残留，会一直占用空间。
 */
data class DownloadStorageInfo(
    val directory: String,
    val freeBytes: Long,
    val completedBytes: Long,
    val partialBytes: Long,
    val orphanPartmapCount: Int,
    val orphanPartmapBytes: Long
) {
    val usedBytes: Long get() = completedBytes + partialBytes
}
