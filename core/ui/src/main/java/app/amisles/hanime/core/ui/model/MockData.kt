package app.amisles.hanime.core.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color

data class Video(
    val id: String,
    val title: String,
    val thumbnailEmoji: String,
    val thumbnailGradient: Pair<Color, Color>,
    val duration: String,
    val likeRate: String,
    val viewCount: String,
    val brand: String,
    val publishTime: String
)

data class BannerData(
    val title: String,
    val viewCount: String,
    val publishTime: String,
    val tags: List<String>,
    val gradient: Pair<Color, Color>
)

// 渐变色数据
val gradients = listOf(
    Pair(Color(0xFF667eea), Color(0xFF764ba2)),
    Pair(Color(0xFFf093fb), Color(0xFFf5576c)),
    Pair(Color(0xFF4facfe), Color(0xFF00f2fe)),
    Pair(Color(0xFF43e97b), Color(0xFF38f9d7)),
    Pair(Color(0xFFfa709a), Color(0xFFfee140)),
    Pair(Color(0xFFa18cd1), Color(0xFFfbc2eb)),
    Pair(Color(0xFFff9a9e), Color(0xFFfecfef)),
    Pair(Color(0xFFffecd2), Color(0xFFfcb69f))
)

// 表情数据
val emojis = listOf("🎬", "🎥", "📹", "🎞️", "📽️", "📀", "💿", "🎮")

// 视频分类
val categoryList = listOf(
    "里番", "泡面番", "Motion Anime", "3DCG", "2.5D动画", "2D动画", "AI生成", "MMD", "Cosplay", "新番预告"
)

// 个人中心菜单项
val profileMenuItems = listOf(
    Pair(Icons.Default.History, "观看历史"),
    Pair(Icons.Default.Favorite, "我的收藏"),
    Pair(Icons.Default.Download, "下载管理"),
    Pair(Icons.Default.Settings, "设置"),
    Pair(Icons.Default.Info, "关于")
)