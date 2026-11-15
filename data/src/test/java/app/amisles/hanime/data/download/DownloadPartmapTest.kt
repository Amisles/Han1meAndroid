package app.amisles.hanime.data.download

import app.amisles.hanime.domain.model.DownloadTask
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 分块续传位图表的读写与几何推导测试。
 *
 * 这几个函数是「暂停后能否正确续传」的唯一依据，也是历史上出过 P0 缺陷的地方
 * （位图未落盘导致丢进度、位图与实际文件长度不一致导致坏文件），因此单独覆盖。
 */
class DownloadPartmapTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun task(filePath: String, totalBytes: Long) = DownloadTask(
        id = 1,
        title = "t",
        quality = "720p",
        url = "https://example.com/a.mp4",
        filePath = filePath,
        totalBytes = totalBytes
    )

    // ── 往返读写 ────────────────────────────────────────────────────────────

    @Test
    fun `write then read returns identical bitmap`() {
        val f = tempFolder.newFile("a.partmap")
        val done = booleanArrayOf(true, false, true, true)

        assertTrue(writePartmap(f, 4, done, LongArray(4)))
        val (cc, bitmap, _) = readPartmap(f)

        assertEquals(4, cc)
        assertArrayEquals(done, bitmap)
    }

    @Test
    fun `write is atomic and leaves no tmp file behind`() {
        val f = tempFolder.newFile("b.partmap")

        assertTrue(writePartmap(f, 3, booleanArrayOf(true, true, true), LongArray(3)))

        assertFalse(File(f.absolutePath + ".tmp").exists())
    }

    @Test
    fun `overwriting existing partmap replaces content`() {
        val f = tempFolder.newFile("c.partmap")
        writePartmap(f, 3, booleanArrayOf(true, true, true), LongArray(3))

        assertTrue(writePartmap(f, 3, booleanArrayOf(false, false, true), LongArray(3)))
        val (_, bitmap, _) = readPartmap(f)

        assertArrayEquals(booleanArrayOf(false, false, true), bitmap)
    }

    @Test
    fun `roundtrip preserves partial chunk bytes`() {
        val f = tempFolder.newFile("p.partmap")
        val done = booleanArrayOf(true, false, false, true)
        val partial = longArrayOf(250, 40, 0, 334)

        assertTrue(writePartmap(f, 4, done, partial))
        val (cc, bitmap, partials) = readPartmap(f)

        assertEquals(4, cc)
        assertArrayEquals(done, bitmap)
        assertArrayEquals(partial, partials)
    }

    @Test
    fun `legacy two line partmap reads with null partial bytes`() {
        val f = tempFolder.newFile("legacy.partmap")
        // 旧版位图只有块数与位图两行；缺第三行必须按「无块内进度」兼容，不得当作损坏
        f.writeText("4\n1010")

        val (cc, bitmap, partials) = readPartmap(f)

        assertEquals(4, cc)
        assertArrayEquals(booleanArrayOf(true, false, true, false), bitmap)
        assertNull(partials)
    }

    // ── 损坏输入 ────────────────────────────────────────────────────────────

    @Test
    fun `read returns null bitmap for empty file`() {
        val f = tempFolder.newFile("empty.partmap")
        val (cc, bitmap, partials) = readPartmap(f)
        assertEquals(0, cc)
        assertNull(bitmap)
        assertNull(partials)
    }

    @Test
    fun `read rejects non numeric chunk count`() {
        val f = tempFolder.newFile("bad.partmap")
        f.writeText("abc\n101")
        val (cc, bitmap, partials) = readPartmap(f)
        assertEquals(0, cc)
        assertNull(bitmap)
        assertNull(partials)
    }

    @Test
    fun `read rejects chunk count above upper bound instead of allocating`() {
        val f = tempFolder.newFile("huge.partmap")
        // MAX_PARTMAP_CHUNKS 之上：必须被拒绝，否则会按它分配数组导致 OOM
        f.writeText("99999999\n101")
        val (cc, bitmap, partials) = readPartmap(f)
        assertEquals(0, cc)
        assertNull(bitmap)
        assertNull(partials)
    }

    @Test
    fun `read treats missing bits as not done`() {
        val f = tempFolder.newFile("short.partmap")
        // 声明 4 个块但只给了 2 位
        f.writeText("4\n11")
        val (cc, bitmap, partials) = readPartmap(f)
        assertEquals(4, cc)
        assertArrayEquals(booleanArrayOf(true, true, false, false), bitmap)
        assertNull(partials)
    }

    // ── sanitizePartmap：位图与磁盘长度对齐 ─────────────────────────────────

    @Test
    fun `sanitize keeps blocks whose end lies within file length`() {
        val f = tempFolder.newFile("d.mp4")
        // 4 块 / 400 字节 → 每块 100 字节
        f.writeBytes(ByteArray(400))
        val bitmap = booleanArrayOf(true, true, true, true)

        val out = sanitizePartmap(task(f.absolutePath, 400), f, 4, bitmap)

        assertArrayEquals(bitmap, out)
    }

    @Test
    fun `sanitize clears blocks that claim done but data is not on disk`() {
        val f = tempFolder.newFile("e.mp4")
        // 只有前 150 字节：第 1 块（区间 [100,200)）末尾 200 > 150，必须清位
        f.writeBytes(ByteArray(150))
        val bitmap = booleanArrayOf(true, true, true, true)

        val out = sanitizePartmap(task(f.absolutePath, 400), f, 4, bitmap)

        assertArrayEquals(booleanArrayOf(true, false, false, false), out)
    }

    @Test
    fun `sanitize returns input unchanged when total size unknown`() {
        val f = tempFolder.newFile("f.mp4")
        val bitmap = booleanArrayOf(true, false)

        val out = sanitizePartmap(task(f.absolutePath, 0), f, 2, bitmap)

        assertArrayEquals(bitmap, out)
    }

    @Test
    fun `sanitize handles missing file by clearing everything`() {
        val f = File(tempFolder.root, "does-not-exist.mp4")
        val bitmap = booleanArrayOf(true, true)

        val out = sanitizePartmap(task(f.absolutePath, 200), f, 2, bitmap)

        assertArrayEquals(booleanArrayOf(false, false), out)
    }

    // ── sanitizePartials：块内字节与磁盘长度对齐 ────────────────────────────

    @Test
    fun `sanitizePartials keeps bytes within file length and drops the rest`() {
        val f = tempFolder.newFile("g.mp4")
        // 4 块 / 400 字节 → 每块 100 字节；磁盘上有 150 字节
        f.writeBytes(ByteArray(150))
        val done = booleanArrayOf(false, false, false, false)
        // 块 0：80 在盘内保留；块 1：120 超出磁盘长度（100+120 > 150）清零；
        // 块 2：150 超过块长，是位图表损坏，清零；块 3：0 保持 0
        val partial = longArrayOf(80, 120, 150, 0)

        val out = sanitizePartials(task(f.absolutePath, 400), f, 4, done, partial)

        assertArrayEquals(longArrayOf(80, 0, 0, 0), out)
    }

    @Test
    fun `sanitizePartials ignores done chunks and unknown totals`() {
        val f = tempFolder.newFile("h.mp4")
        f.writeBytes(ByteArray(150))
        val done = booleanArrayOf(true, false, false, false)
        // 块 1 起点 100，块内 40 字节末尾 140 ≤ 150，保留
        val partial = longArrayOf(100, 40, 0, 0)

        val out = sanitizePartials(task(f.absolutePath, 400), f, 4, done, partial)
        assertArrayEquals(longArrayOf(0, 40, 0, 0), out)

        assertArrayEquals(LongArray(4), sanitizePartials(task(f.absolutePath, 0), f, 4, done, partial))
        assertArrayEquals(LongArray(4), sanitizePartials(task(f.absolutePath, 400), f, 4, done, null))
    }

    @Test
    fun `sanitizePartials handles short or corrupt partial line as zeros`() {
        val f = tempFolder.newFile("i.mp4")
        f.writeBytes(ByteArray(150))
        val done = booleanArrayOf(false, false, false, false)

        val out = sanitizePartials(task(f.absolutePath, 400), f, 4, done, longArrayOf(80))

        assertArrayEquals(longArrayOf(80, 0, 0, 0), out)
    }

    // ── 分块几何 ────────────────────────────────────────────────────────────

    @Test
    fun `ceilDiv rounds up and guards against zero divisor`() {
        assertEquals(3, ceilDiv(10, 4))
        assertEquals(2, ceilDiv(8, 4))
        assertEquals(1, ceilDiv(1, 4))
        assertEquals(0, ceilDiv(10, 0))
    }

    @Test
    fun `chunk geometry covers total exactly with remainder on last chunk`() {
        val total = 1000L
        val chunkCount = 3

        assertEquals(0L, chunkStartOffset(0, chunkCount, total))
        assertEquals(333L, chunkLength(0, chunkCount, total))
        assertEquals(333L, chunkStartOffset(1, chunkCount, total))
        assertEquals(333L, chunkLength(1, chunkCount, total))
        assertEquals(666L, chunkStartOffset(2, chunkCount, total))
        assertEquals(334L, chunkLength(2, chunkCount, total))
        assertEquals(total, chunkStartOffset(2, chunkCount, total) + chunkLength(2, chunkCount, total))
    }
}
