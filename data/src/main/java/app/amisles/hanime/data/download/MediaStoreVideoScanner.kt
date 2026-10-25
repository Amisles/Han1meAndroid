package app.amisles.hanime.data.download

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** 从 MediaStore 查到的、位于下载目录下的成品视频。 */
internal data class MediaStoreVideo(
    val displayName: String,
    val absolutePath: String,
    val size: Long
)

/**
 * 通过 MediaStore 枚举公共 Download/Hanime 下的视频。
 *
 * 分区存储下 `File.listFiles()` 只返回本应用自己写入的文件，卸载重装后由上一个安装留下的 mp4
 * 对 `File` API 不可见（目录存在、但列举结果为空），却仍能出现在 MediaStore 的索引里。
 * 因此历史下载的识别必须走 MediaStore，而不是直接读目录。
 */
internal object MediaStoreVideoScanner {

    fun queryDownloads(context: Context, subDir: String): List<MediaStoreVideo> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA
        )
        // 路径限定到 Download/<subDir>：不同厂商的相对路径分隔符可能不同，故匹配子目录名本身
        val selection = MediaStore.Video.Media.DATA + " LIKE ?"
        val args = arrayOf("%" + File.separator + "Download" + File.separator + subDir + File.separator + "%")

        val result = mutableListOf<MediaStoreVideo>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val path = cursor.getString(dataCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    if (!name.endsWith(".mp4", ignoreCase = true) || size <= 0L) continue
                    result += MediaStoreVideo(displayName = name, absolutePath = path, size = size)
                }
            }
        }
        return result
    }

}
