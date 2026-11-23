package id.whitesecurity.antivirus.scanner

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object HashUtil {

    /** Streams the file through SHA-256 — safe for large APKs, no full read into memory. */
    fun sha256(file: File): String? {
        if (!file.exists() || !file.canRead()) return null
        return file.inputStream().use { sha256(it) }
    }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
