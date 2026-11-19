package app.amisles.hanime.data.cache

import java.io.File

private const val TEMP_SUFFIX = ".tmp"

/**
 * 写缓存文件：先写同名 .tmp 再改名，进程中途被杀不会留下半截内容被当成有效缓存。
 *
 * 缓存可重建，故改名失败时允许先删旧文件再重试；[app.amisles.hanime.data.download.DownloadPartmap]
 * 那类丢了就无法续传的数据需要 fsync 语义，不走这里。
 */
internal fun writeCacheFile(target: File, content: String): Boolean {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, target.name + TEMP_SUFFIX)
    val written = runCatching {
        temporary.writeText(content)
        temporary.renameTo(target) || run {
            target.delete()
            temporary.renameTo(target)
        }
    }.getOrDefault(false)
    if (!written) runCatching { temporary.delete() }
    return written
}

/** 读缓存文件并解码；文件缺失与内容损坏都按未命中处理，返回 null。 */
internal inline fun <T> readCacheFile(target: File, decode: (String) -> T): T? =
    if (!target.isFile) null else runCatching { decode(target.readText()) }.getOrNull()
