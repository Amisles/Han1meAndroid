package app.amisles.hanime.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份校验和与格式兼容性。
 *
 * 两条对外契约：同一份内容在任何设备算出同一校验值（与条目写入顺序无关）；
 * 旧版本导出的包（无校验文件）必须仍可导入。
 */
class BackupChecksumTest {

    private fun entries(vararg pairs: Pair<String, String>) = linkedMapOf(*pairs)

    @Test
    fun checksumIgnoresEntryOrder() {
        val a = entries("a.json" to "[1]", "b.json" to "[2]")
        val b = entries("b.json" to "[2]", "a.json" to "[1]")
        assertEquals(BackupChecksum.compute(a), BackupChecksum.compute(b))
    }

    @Test
    fun checksumChangesWithContent() {
        val a = entries("a.json" to "[1]")
        val b = entries("a.json" to "[2]")
        assertFalse(BackupChecksum.compute(a) == BackupChecksum.compute(b))
    }

    @Test
    fun checksumIsHexOf64Chars() {
        val hex = BackupChecksum.compute(entries("a.json" to "x"))
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in "0123456789abcdef" })
    }

    @Test
    fun parseAcceptsValidFormat() {
        val hex = BackupChecksum.compute(entries("a.json" to "x"))
        assertEquals(hex, BackupChecksum.parse("sha256:" + hex))
        assertEquals(hex, BackupChecksum.parse("  sha256:" + hex.uppercase() + "  "))
    }

    @Test
    fun parseRejectsMalformed() {
        assertNull(BackupChecksum.parse("md5:abc"))
        assertNull(BackupChecksum.parse("sha256:nothex"))
        assertNull(BackupChecksum.parse(""))
    }

    @Test
    fun missingChecksumIsAcceptedForLegacyArchives() {
        assertTrue(BackupChecksum.matches(entries("a.json" to "x"), null))
    }

    @Test
    fun corruptedPayloadIsRejected() {
        val original = entries("a.json" to "x")
        val raw = BackupChecksum.format(BackupChecksum.compute(original))
        assertTrue(BackupChecksum.matches(original, raw))
        assertFalse(BackupChecksum.matches(entries("a.json" to "y"), raw))
    }
}
