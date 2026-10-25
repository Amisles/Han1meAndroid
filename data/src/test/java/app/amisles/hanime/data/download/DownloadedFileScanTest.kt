package app.amisles.hanime.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadedFileScanTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun touch(name: String, bytes: Int = 1024): File =
        temp.newFile(name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `adopts files named by the downloader`() {
        touch("标题_1080P_405188.mp4")

        val found = DownloadStorageInspector.scanDownloadedFiles(temp.root, emptySet())

        assertEquals(1, found.size)
        assertEquals("405188", found[0].videoId)
        assertEquals("标题", found[0].title)
        assertEquals("1080P", found[0].quality)
        assertEquals(1024L, found[0].totalBytes)
    }

    @Test
    fun `keeps underscores inside the title and quality`() {
        touch("A_B 標題_720P_115128.mp4")

        val found = DownloadStorageInspector.scanDownloadedFiles(temp.root, emptySet())

        assertEquals("115128", found[0].videoId)
        assertEquals("A_B 標題", found[0].title)
        assertEquals("720P", found[0].quality)
    }

    @Test
    fun `skips files already referenced by a task`() {
        val file = touch("标题_1080P_405188.mp4")

        val found = DownloadStorageInspector.scanDownloadedFiles(temp.root, setOf(file.name))

        assertTrue(found.isEmpty())
    }

    @Test
    fun `skips files that still have a resume partmap`() {
        touch("标题_1080P_405188.mp4")
        touch("标题_1080P_405188.mp4.partmap")

        val found = DownloadStorageInspector.scanDownloadedFiles(temp.root, emptySet())

        assertTrue("未下完的文件不应被认领", found.isEmpty())
    }

    @Test
    fun `skips names that do not carry a video id`() {
        touch("random_video.mp4")
        touch("noextension")
        touch("_1080P_1.mp4")

        val found = DownloadStorageInspector.scanDownloadedFiles(temp.root, emptySet())

        assertTrue("未知命名不应被认领: " + found.map { it.filePath }, found.isEmpty())
    }

    @Test
    fun `skips empty files and non mp4 files`() {
        touch("标题_1080P_1.mp4", bytes = 0)
        touch("标题_1080P_2.partmap")

        val found = DownloadStorageInspector.scanDownloadedFiles(temp.root, emptySet())

        assertTrue(found.isEmpty())
    }
}
