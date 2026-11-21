package app.amisles.hanime.feature.detail.comment

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 视频域传给评论域的调用上下文。
 *
 * 发表评论/回复需要 csrfToken（来自视频详情页）与当前 videoId。
 *
 * 二者由视频域在调用时传入，而不是让评论域反向依赖视频域 —— 这样评论域可以单独测试，
 * 也不需要为了传递两个字段而引入一个"视频域单例"。
 */
data class CommentContext(
    val videoId: String,
    val csrfToken: String,
    /** 当前评论数（发表成功后用于回写 VideoDetail.commentCount）。 */
    val commentCount: Int,
)

/**
 * 评论域 ViewModel：只保留单一状态与命令入口。
 *
 * 分页、回复缓存与错误收口在 [CommentPagingController]；发表、点赞与乐观回滚在
 * [CommentComposerController]。两者共用同一 [CommentUiState]，界面仍只订阅一次。
 */
@HiltViewModel
class CommentViewModel @Inject constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentUiState())
    val uiState: StateFlow<CommentUiState> = _uiState.asStateFlow()

    private val _events = Channel<CommentEvent>(Channel.BUFFERED)
    val events: Flow<CommentEvent> = _events.receiveAsFlow()

    private val paging = CommentPagingController(account, context, viewModelScope, _uiState)

    private val composer = CommentComposerController(
        account = account,
        session = session,
        context = context,
        scope = viewModelScope,
        state = _uiState,
        currentVideoId = { paging.currentVideoId },
        loadReplies = { paging.loadReplies(it) },
        onEvent = { _events.trySend(it) }
    )

    fun selectVideo(videoId: String) {
        AppLogger.d("CommentViewModel", "selectVideo: $videoId")
        paging.selectVideo(videoId)
    }

    fun loadComments(force: Boolean = false) = paging.loadComments(force)

    fun loadMoreComments() = paging.loadMoreComments()

    fun loadReplies(commentId: String, force: Boolean = false) = paging.loadReplies(commentId, force)

    fun toggleReplies(commentId: String) = paging.toggleReplies(commentId)

    fun startReply(commentId: String, replyToUsername: String? = null) =
        composer.startReply(commentId, replyToUsername)

    fun cancelReply() = composer.cancelReply()

    fun submitReply(text: String, ctx: CommentContext) = composer.submitReply(text, ctx)

    fun clearReplyError() = composer.clearReplyError()

    fun postComment(text: String, ctx: CommentContext, onCommentCountUpdated: (Int) -> Unit = {}) =
        composer.postComment(text, ctx, onCommentCountUpdated)

    fun clearPostCommentError() = composer.clearPostCommentError()

    fun toggleCommentLike(comment: Comment, ctx: CommentContext) =
        composer.toggleCommentLike(comment, ctx)

    companion object {
        const val COMMENTS_PAGE_SIZE = CommentPagingController.COMMENTS_PAGE_SIZE
    }
}

/**
 * 评论回复的目标。
 * @param commentId 被回复的评论 ID（replyComment 接口只认父评论 ID）
 * @param replyToUsername 非空表示回复某条回复，提交内容会带 "@用户名 " 前缀
 */
data class ReplyTarget(
    val commentId: String,
    val replyToUsername: String? = null
)
