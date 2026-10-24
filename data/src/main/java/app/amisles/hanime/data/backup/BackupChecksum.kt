package app.amisles.hanime.data.backup

import java.security.MessageDigest

/**
 * 备份包的完整性校验。
 *
 * 备份文件会被拷到网盘、聊天工具里，传输中断或磁盘损坏都可能让它只剩半截。
 * 这里对「除校验文件本身之外的全部条目」按名称排序后求 SHA-256，
 * 保证同一份内容在任何设备上算出同一个值，且与 zip 条目的写入顺序无关。
 */
internal object BackupChecksum {

    const val ENTRY_CHECKSUM = "checksum.txt"

    /**
     * @param entries 条目名 → 文本内容。调用方不应把校验文件本身放进 entries。
     */
    fun compute(entries: Map<String, String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        entries.keys.sorted().forEach { name ->
            digest.update(name.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(entries.getValue(name).toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 解析校验文件内容（形如 sha256:<hex>）；格式不符返回 null。 */
    fun parse(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (!text.startsWith(PREFIX)) return null
        val hex = text.removePrefix(PREFIX).trim().lowercase()
        if (hex.length != 64 || hex.any { it !in "0123456789abcdef" }) return null
        return hex
    }

    /** 无校验文件时返回 true：旧版本导出的包没有这一项，不能因此拒绝导入。 */
    fun matches(entries: Map<String, String>, raw: String?): Boolean {
        val expected = parse(raw) ?: return raw.isNullOrBlank()
        return compute(entries) == expected
    }

    fun format(hex: String): String = PREFIX + hex

    private const val PREFIX = "sha256:"
}
