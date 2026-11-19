package app.amisles.hanime.domain.repository

/** 详情页常驻缓存的管理面：界面只需要调整上限与清空，不接触文件布局。 */
interface VideoDetailCacheRepository {

    /** 用户调小上限后立即按新上限淘汰。 */
    suspend fun trimTo(limit: Int)

    suspend fun clear()

    suspend fun count(): Int
}
