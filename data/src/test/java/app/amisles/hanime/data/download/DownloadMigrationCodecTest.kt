package app.amisles.hanime.data.download

import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 迁移清单的编解码往返。
 *
 * 覆盖三个易错点：只导出有位图的任务、版本更高的包被拒绝、导入后统一落到 PAUSED。
 */
class DownloadMigrationCodecTest {

    private fun task(id: Int, path: String) = DownloadTask(
        id = id,
        title = "title-$id",
        quality = "1080p",
        url = "https://example.com/$id.mp4",
        totalBytes = 1000L,
        downloadedBytes = 500L,
        status = DownloadStatus.DOWNLOADING,
        filePath = path,
        thumbnailUrl = "",
        videoId = "v$id"
    )

    @Test
    fun roundTripKeepsCoreFields() {
        val raw = DownloadMigrationCodec.encode(listOf(task(1, "/a/1.mp4"))) { true }
        val decoded = DownloadMigrationCodec.decode(raw)
        assertEquals(1, decoded.size)
        val entry = decoded.first()
        assertEquals(1, entry.id)
        assertEquals("title-1", entry.title)
        assertEquals(1000L, entry.totalBytes)
        assertEquals(500L, entry.downloadedBytes)
        assertEquals("v1", entry.videoId)
        assertEquals("1.mp4.partmap", entry.partmapName)
    }

    @Test
    fun skipsTasksWithoutPartmap() {
        val raw = DownloadMigrationCodec.encode(
            listOf(task(1, "/a/1.mp4"), task(2, "/a/2.mp4"))
        ) { path -> path.endsWith("1.mp4") }
        assertEquals(1, DownloadMigrationCodec.decode(raw).size)
    }

    @Test
    fun rejectsFutureSchemaVersion() {
        val raw = """{"version":99,"tasks":[{"id":1,"title":"t","url":"https://e.com/a"}]}"""
        assertTrue(DownloadMigrationCodec.decode(raw).isEmpty())
    }

    @Test
    fun rejectsMalformedJson() {
        assertTrue(DownloadMigrationCodec.decode("not json").isEmpty())
    }

    @Test
    fun dropsEntriesWithoutUrlOrTitle() {
        val raw = """{"version":1,"tasks":[
            {"id":1,"title":"","url":"https://e.com/a"},
            {"id":2,"title":"t","url":""},
            {"id":3,"title":"ok","url":"https://e.com/c"}
        ]}"""
        assertEquals(1, DownloadMigrationCodec.decode(raw).size)
    }

    @Test
    fun importedTaskIsPausedWithNewPath() {
        val entry = DownloadMigrationCodec.decode(
            DownloadMigrationCodec.encode(listOf(task(1, "/old/1.mp4"))) { true }
        ).first()
        val task = DownloadMigrationCodec.toTask(entry, "/new/1.mp4")
        assertEquals(DownloadStatus.PAUSED, task.status)
        assertEquals("/new/1.mp4", task.filePath)
        assertEquals(500L, task.downloadedBytes)
    }
}
