package com.mtg.sources

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.ByteString.Companion.decodeBase64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The envelope/URL codecs shipped in the sports sites' public web clients. */
object PublicSportsJson {
    private const val STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val SHUFFLED = "MiljRIn9PX1o63wGBYTtFsKmEkSur-pC_U02cvzAdy5e8ZqLDgJ4OhVN7QbHxfWa"
    private const val KEY = "vqW3n/yB1R+GwSPc4kJs2dCkV7kRmXaNy3lQ/3XkHJI="

    fun decode(body: String): JsonElement {
        val text = body.trim()
        if (!text.startsWith("k1")) return Json.parseToJsonElement(text)
        val base64 = text.substring(2).reversed().map { character ->
            val index = SHUFFLED.indexOf(character)
            require(index >= 0) { "Invalid sports response" }
            STANDARD[index]
        }.joinToString("")
        val bytes = requireNotNull(base64.decodeBase64()).toByteArray()
        require(bytes.size > 28) { "Truncated sports response" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY.decodeBase64()!!.toByteArray(), "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return Json.parseToJsonElement(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }

    fun matchUrl(hex: String): String? = runCatching {
        require(hex.length % 2 == 0)
        val key = "AlbaSport2025!@#"
        val decoded = (hex.indices step 2).map { index ->
            (hex.substring(index, index + 2).toInt(16) xor key[index / 2 % key.length].code).toChar()
        }.joinToString("")
        decoded.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }.getOrNull()
}
