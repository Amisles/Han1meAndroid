package app.amisles.hanime.data.backup

import app.amisles.hanime.domain.model.BackupError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object BackupArchiveCodec {

    fun zip(files: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            files.forEach { (name, text) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun unzip(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var total = 0L
        val chunk = ByteArray(8 * 1024)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val buf = ByteArrayOutputStream()
                while (true) {
                    val n = zis.read(chunk)
                    if (n <= 0) break
                    total += n
                    if (total > BackupFormat.MAX_TOTAL_BYTES ||
                        buf.size() + n > BackupFormat.MAX_ENTRY_BYTES
                    ) {
                        throw BackupError.NotASupportedArchive
                    }
                    buf.write(chunk, 0, n)
                }
                result[entry.name] = buf.toString("UTF-8")
                zis.closeEntry()
            }
        }
        return result
    }
}
