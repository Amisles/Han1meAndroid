package app.amisles.hanime.data.preferences

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN 的加盐慢哈希：磁盘上只留校验值，任何环节都不保存明文 PIN。 */
internal object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hash(pin: String, salt: String): String = runCatching {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            Base64.getDecoder().decode(salt),
            ITERATIONS,
            KEY_BITS
        )
        val encoded = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        spec.clearPassword()
        Base64.getEncoder().encodeToString(encoded)
    }.getOrDefault("")

    fun matches(pin: String, salt: String, expected: String): Boolean {
        if (salt.isBlank() || expected.isBlank()) return false
        val actual = hash(pin, salt)
        return actual.isNotEmpty() &&
            MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())
    }
}
