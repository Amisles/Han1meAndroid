package app.amisles.hanime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.amisles.hanime.core.ui.R
import android.widget.Toast

/**
 * 外链入口：承接系统分享的文本链接与 hanime:// 深链，解析出视频页地址后交给 MainActivity。
 *
 * 只认 /watch?v= 形式的视频地址；作者、播放列表等页面仍从应用内浏览。解析失败时
 * 回退到普通启动（带解析出的文本，便于用户手动搜索），不打断进入应用的路径。
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = resolveSharedText(intent)
        val videoUrl = shared?.let { extractWatchUrl(it) }
        if (videoUrl != null) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_VIDEO
                    data = Uri.parse(videoUrl)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        } else {
            Toast.makeText(this, getString(R.string.share_link_unrecognized), Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        }
        finish()
    }

    private fun resolveSharedText(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND) {
            return intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return null
            return when (uri.scheme) {
                "http", "https" -> uri.toString()
                DEEP_LINK_SCHEME -> uri.getQueryParameter("url") ?: uri.toString()
                else -> null
            }
        }
        return null
    }

    companion object {
        private const val DEEP_LINK_SCHEME = "hanime"

        /** 从任意文本中找出首个 /watch?v= 链接（容忍被聊天工具截断或附加了多余文案）。 */
        fun extractWatchUrl(text: String): String? {
            val match = WATCH_URL_REGEX.find(text) ?: return null
            return "https://" + match.groupValues[1] + match.groupValues[2] + match.groupValues[3]
        }

        private val WATCH_URL_REGEX = Regex(
            pattern = """https?://([A-Za-z0-9.-]+?)(/watch\?v=\d+)([^\s<>'"\\]*)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    }
}
