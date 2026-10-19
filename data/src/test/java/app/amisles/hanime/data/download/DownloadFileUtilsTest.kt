package app.amisles.hanime.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

/**
 * 落盘文件名截断与目录校验测试。
 *
 * 文件名按 UTF-8 **字节**预算截断（ext4 上限 255 字节，中文 3 字节/字极易越界），
 * 尾部 _<videoId>.mp4 必须完整保留，否则同名任务会互相覆盖。
 */
class DownloadFileUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun utf8Len(s: String) = s.toByteArray(Charsets.UTF_8).size

    @Test
    fun `short name is returned unchanged`() {
        assertEquals("abc", clampFileNameBase("abc", "12345"))
    }

    @Test
    fun `long ascii name is truncated within budget`() {
        val base = "a".repeat(500)
        val out = clampFileNameBase(base, "12345")
        // 尾部 "_12345.mp4" 占 10 字节，预算 = MAX_FILE_NAME_BYTES - 10
        val tailBytes = "_12345.mp4".toByteArray(Charsets.UTF_8).size
        assertTrue(utf8Len(out) <= MAX_FILE_NAME_BYTES - tailBytes)
        assertTrue(out.length < base.length)
    }

    @Test
    fun `long CJK name is truncated by bytes not chars`() {
        // 每个汉字 3 字节：300 字 = 900 字节，远超预算
        val base = "中".repeat(300)
        val out = clampFileNameBase(base, "12345")
        val tailBytes = "_12345.mp4".toByteArray(Charsets.UTF_8).size
        assertTrue("必须按字节截断", utf8Len(out) <= MAX_FILE_NAME_BYTES - tailBytes)
        // 且不能截出半个字符
        assertTrue("不得产生替换字符", !out.contains('\uFFFD'))
    }

    @Test
    fun `result plus tail stays within filesystem limit`() {
        val base = "中".repeat(300)
        val suffix = "12345"
        val full = clampFileNameBase(base, suffix) + "_" + suffix + ".mp4"
        assertTrue("完整文件名不得超过 255 字节", utf8Len(full) <= 255)
    }

    @Test
    fun `empty base is allowed`() {
        assertEquals("", clampFileNameBase("", "999"))
    }

    // ── ensureDirWritable ───────────────────────────────────────────────────

    @Test
    fun `ensureDirWritable creates missing directory`() {
        val dir = File(tempFolder.root, "a/b/c")
        assertFalse(dir.exists())

        assertTrue(ensureDirWritable(dir))
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `ensureDirWritable returns true for existing writable directory`() {
        val dir = tempFolder.newFolder("exists")
        assertTrue(ensureDirWritable(dir))
    }

    @Test
    fun `ensureDirWritable returns false when path is a regular file`() {
        val file = tempFolder.newFile("regular.txt")
        assertFalse(ensureDirWritable(file))
    }
}
