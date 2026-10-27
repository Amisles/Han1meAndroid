package app.amisles.hanime.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.amisles.hanime.domain.model.ComicProgress

/**
 * 漫画阅读进度表。
 *
 * 主键就是漫画 id，一本漫画只有一行；重复阅读只更新 lastPage 与 updatedAt。
 * 标题与封面冗余存储，进度列表因此不必再请求详情页。
 */
@Entity(tableName = "comic_progress")
data class ComicProgressEntity(
    @PrimaryKey val comicId: String,
    val title: String,
    val coverUrl: String,
    val pageCount: Int,
    @ColumnInfo(name = "last_page") val lastPage: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun ComicProgressEntity.toDomain(): ComicProgress = ComicProgress(
    comicId = comicId,
    title = title,
    coverUrl = coverUrl,
    pageCount = pageCount,
    lastPage = lastPage,
    updatedAt = updatedAt
)

fun ComicProgress.toEntity(): ComicProgressEntity = ComicProgressEntity(
    comicId = comicId,
    title = title,
    coverUrl = coverUrl,
    pageCount = pageCount,
    lastPage = lastPage,
    updatedAt = updatedAt
)
