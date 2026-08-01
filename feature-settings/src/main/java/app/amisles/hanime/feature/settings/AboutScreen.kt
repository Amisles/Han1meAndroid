package app.amisles.hanime.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary

data class OpenSourceProject(
    val name: String,
    val version: String,
    val description: String,
    val license: String,
    val url: String
)

data class AppFeature(
    val icon: ImageVector,
    val title: String
)

private val appFeatures = listOf(
    AppFeature(Icons.Filled.Home, "多区块首页浏览"),
    AppFeature(Icons.Filled.Search, "多维度搜索与筛选"),
    AppFeature(Icons.Filled.PlayArrow, "多画质在线播放"),
    AppFeature(Icons.Filled.Download, "后台下载与离线缓存"),
    AppFeature(Icons.Filled.Favorite, "本地收藏与历史记录"),
    AppFeature(Icons.Filled.Settings, "Jetpack Compose 现代化 UI")
)

private val openSourceProjects = listOf(
    OpenSourceProject(
        name = "Android Gradle Plugin",
        version = "9.2.1",
        description = "Android 官方构建工具链，9.x 内置 Kotlin 编译支持",
        license = "Apache 2.0",
        url = "https://developer.android.com/build/releases/gradle-plugin"
    ),
    OpenSourceProject(
        name = "Kotlin",
        version = "2.4.10",
        description = "JetBrains 现代 JVM/Android 静态类型编程语言",
        license = "Apache 2.0",
        url = "https://kotlinlang.org"
    ),
    OpenSourceProject(
        name = "Jetpack Compose BOM",
        version = "2026.06.00",
        description = "Google 官方现代 Android UI 工具包，声明式构建原生界面",
        license = "Apache 2.0",
        url = "https://developer.android.com/jetpack/compose"
    ),
    OpenSourceProject(
        name = "Media3 ExoPlayer",
        version = "1.10.1",
        description = "Android 官方媒体播放器，支持 HLS 高清流媒体播放",
        license = "Apache 2.0",
        url = "https://developer.android.com/media/media3"
    ),
    OpenSourceProject(
        name = "OkHttp",
        version = "5.4.0",
        description = "Square 出品的高性能 HTTP 客户端，支持 HTTP/2 与连接池",
        license = "Apache 2.0",
        url = "https://square.github.io/okhttp"
    ),
    OpenSourceProject(
        name = "Jsoup",
        version = "1.22.2",
        description = "HTML DOM 解析与 CSS 选择器库",
        license = "MIT",
        url = "https://jsoup.org"
    ),
    OpenSourceProject(
        name = "Coil 3",
        version = "3.5.0",
        description = "Kotlin 协程驱动的 Android 图片加载库（io.coil-kt.coil3）",
        license = "Apache 2.0",
        url = "https://coil-kt.github.io/coil"
    ),
    OpenSourceProject(
        name = "Room",
        version = "2.8.4",
        description = "SQLite 之上的 ORM 对象关系映射持久化库",
        license = "Apache 2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/room"
    ),
    OpenSourceProject(
        name = "Navigation Compose",
        version = "2.9.8",
        description = "Compose 应用中的导航框架",
        license = "Apache 2.0",
        url = "https://developer.android.com/jetpack/compose/navigation"
    ),
    OpenSourceProject(
        name = "Lifecycle",
        version = "2.10.0",
        description = "MVVM 架构的 ViewModel、StateFlow 与生命周期感知组件",
        license = "Apache 2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/lifecycle"
    ),
    OpenSourceProject(
        name = "Activity Compose",
        version = "1.12.3",
        description = "Compose 与 Activity 集成，提供 ComponentActivity 与 setContent",
        license = "Apache 2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/activity"
    ),
    OpenSourceProject(
        name = "KSP",
        version = "2.3.10",
        description = "Kotlin 符号处理器（KSP2 架构，Room 编译时生成代码）",
        license = "Apache 2.0",
        url = "https://kotlinlang.org/docs/ksp-overview.html"
    ),
    OpenSourceProject(
        name = "AndroidX Core KTX",
        version = "1.13.1",
        description = "Android Framework API 的 Kotlin 扩展",
        license = "Apache 2.0",
        url = "https://developer.android.com/kotlin/ktx"
    )
)

@Composable
fun AboutScreen(
    onBackClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HanimeBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = HanimeTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "关于我们",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = HanimeTextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(HanimePrimary, Color(0xFFb71c1c))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "H",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Hanime Android",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = HanimeTextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "版本 1.0.0",
                    fontSize = 12.sp,
                    color = HanimeTextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = HanimeCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "应用简介",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HanimePrimary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Text(
                            text = "Hanime Android 是一款开源的第三方 Hanime 客户端，提供流畅的视频浏览、搜索、在线播放与离线下载功能。本项目基于 AGG 9.2.1 + Kotlin 2.4.10 + Jetpack Compose BOM 2026.06 构建，采用 MVVM + Repository 架构，UI 层、解析层、网络层清晰解耦。网络层使用 OkHttp 5.4.0，图片加载使用 Coil 3.5.0，数据持久化使用 Room 2.8.4，视频播放基于 Media3 1.10.1。项目以 GPLv3.0 协议完全开源，欢迎社区自由学习与二次开发。",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = HanimeTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "主要功能",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HanimePrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 6.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = HanimeCard)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        appFeatures.forEachIndexed { index, feature ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = feature.icon,
                                    contentDescription = feature.title,
                                    modifier = Modifier.size(20.dp),
                                    tint = HanimePrimary
                                )
                                Text(
                                    text = feature.title,
                                    fontSize = 14.sp,
                                    color = HanimeTextPrimary
                                )
                            }
                            if (index < appFeatures.size - 1) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .padding(horizontal = 16.dp)
                                        .background(Color.White.copy(alpha = 0.06f))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = HanimePrimary.copy(alpha = 0.12f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "开源协议",
                                tint = HanimePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "开源协议",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HanimePrimary
                            )
                        }
                        Text(
                            text = "GNU General Public License v3.0",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HanimeTextPrimary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = "本项目采用 GPLv3.0 协议开源。您可以自由地使用、研究、修改和分发本软件，但分发本软件或其衍生作品时，必须同样以 GPLv3.0 协议开源完整源代码，并保留原始版权声明与协议文本。任何再分发不得施加超出 GPLv3.0 的额外限制。",
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = HanimeTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "https://www.gnu.org/licenses/gpl-3.0.html",
                            fontSize = 11.sp,
                            color = HanimePrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "致谢 · 使用到的开源项目",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HanimePrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 6.dp)
                )
            }

            items(openSourceProjects) { project ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = HanimeCard)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = project.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HanimeTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = project.version,
                                fontSize = 11.sp,
                                color = HanimeTextSecondary
                            )
                        }
                        Text(
                            text = project.description,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = HanimeTextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(HanimePrimary.copy(alpha = 0.15f))
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = project.license,
                                    fontSize = 10.sp,
                                    color = HanimePrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = project.url,
                                fontSize = 10.5.sp,
                                color = HanimeTextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Made with ♥ by the Open Source Community",
                    fontSize = 11.sp,
                    color = HanimeTextSecondary
                )
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}