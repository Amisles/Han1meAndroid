package app.amisles.hanime.core.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import app.amisles.hanime.core.ui.R

// label：服务端简体标识（匹配首页分区标题与深链接参数，不用于界面展示）
// displayRes：界面展示的翻译资源；apiValue：搜索接口实际使用的参数（部分与展示名不同，如里番→裏番）
data class Category(
    val label: String,
    val displayRes: Int,
    val apiValue: String
)

val categories = listOf(
    Category("里番", R.string.category_li_fan, "裏番"),
    Category("泡面番", R.string.category_pao_mian_fan, "泡麵番"),
    Category("Motion Anime", R.string.category_motion_anime, "Motion Anime"),
    Category("3DCG", R.string.category_3dcg, "3DCG"),
    Category("2.5D动画", R.string.category_2_5d, "2.5D"),
    Category("2D动画", R.string.category_2d, "2D動畫"),
    Category("AI生成", R.string.category_ai_generated, "AI生成"),
    Category("MMD", R.string.category_mmd, "MMD"),
    Category("Cosplay", R.string.category_cosplay, "Cosplay"),
    Category("新番预告", R.string.category_new_preview, "新番預告")
)

// 首页分区标题：服务端返回简体标识，按资源翻译展示，深链接仍传原始 section.title
val homeSectionTitleResMap = mapOf(
    "最新上市" to R.string.search_sort_new_release,
    "最新上传" to R.string.search_sort_new_upload,
    "本日排行" to R.string.search_sort_daily,
    "本周排行" to R.string.search_sort_weekly,
    "本月排行" to R.string.search_sort_monthly,
    "观看次数" to R.string.search_sort_views,
    "点赞比例" to R.string.search_sort_like_ratio,
    "时长最长" to R.string.search_sort_longest,
    "他们在看" to R.string.search_sort_watching
)

val profileMenuItems = listOf(
    Pair(Icons.Default.History, "观看历史"),
    Pair(Icons.Default.Favorite, "我的收藏"),
    Pair(Icons.Default.Download, "下载管理"),
    Pair(Icons.Default.Settings, "设置"),
    Pair(Icons.Default.Info, "关于")
)