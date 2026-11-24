package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.TranslationModelState
import kotlinx.coroutines.flow.StateFlow

/**
 * 本机实时翻译模型的下载与状态。
 *
 * 不复用 [DownloadRepository]：那套队列面向用户下载的视频，带断点续传、并发门控与前台服务通知，
 * 而模型是随应用发布的单份资源，全局仅此一份，下载失败重来即可，塞进任务列表只会污染下载管理页。
 */
interface TranslationRepository {

    val modelStateFlow: StateFlow<TranslationModelState>

    /** 是否已配置模型下载地址。未发布时设置页只展示状态，不提供下载入口。 */
    val isModelPublished: Boolean

    fun downloadModel()

    fun cancelDownload()

    fun deleteModel()
}
