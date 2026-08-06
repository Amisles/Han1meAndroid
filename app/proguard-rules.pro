# ===========================================
# Hanime Android ProGuard / R8 规则
# ===========================================

# 保留崩溃堆栈的源文件名和行号，便于 release 包定位崩溃
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留泛型签名（Room、Hilt、协程需要）
-keepattributes Signature

# 保留注解（Room/Hilt/Jsoup 注解运行时需要）
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations
-keepattributes AnnotationDefault

# ===========================================
# 项目自身类
# ===========================================

# DownloadService：DownloadManager 通过 Intent.setClassName(context, String) 反射启动，
# 类名硬编码为字符串常量，混淆后会导致服务无法启动
-keep class app.amisles.hanime.service.DownloadService { *; }

# ===========================================
# Room 数据库
# ===========================================

# Room 实体类：Room 运行时反射读写字段，字段名与数据库列名绑定，不可混淆
-keep class app.amisles.hanime.domain.model.FavoriteVideo { *; }
-keep class app.amisles.hanime.domain.model.WatchHistory { *; }
-keep class app.amisles.hanime.domain.model.DownloadEntity { *; }
-keep class app.amisles.hanime.domain.model.SearchHistoryEntity { *; }

# Room 数据库类：Room.databaseBuilder 通过 ::class.java 绑定生成的 _Impl 类
-keep class app.amisles.hanime.data.local.database.FavoriteDatabase { *; }

# Room DAO 接口：Room 编译期生成 _Impl，运行时动态代理调用
-keep interface app.amisles.hanime.data.local.database.FavoriteDao { *; }
-keep interface app.amisles.hanime.data.local.database.WatchHistoryDao { *; }
-keep interface app.amisles.hanime.data.local.database.DownloadDao { *; }
-keep interface app.amisles.hanime.data.local.database.SearchHistoryDao { *; }

# Room Migration 匿名类：Room 反射调用 migrate 方法
-keep class * extends androidx.room.migration.Migration { *; }

# ===========================================
# 枚举持久化
# ===========================================

# DownloadStatus：.name 持久化到 Room 数据库 download_tasks.status 字段，
# valueOf 从数据库反序列化，枚举常量名不可混淆以保持数据兼容性
-keep enum app.amisles.hanime.domain.model.DownloadStatus { *; }

# ===========================================
# Kotlin 协程与 Flow（R8 full mode 兼容）
# ===========================================

# 协程内部类（R8 full mode 下偶发裁剪问题）
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
