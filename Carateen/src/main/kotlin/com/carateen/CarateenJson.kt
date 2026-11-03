package com.carateen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Matches the JSON envelope used by Carateen's public web client. */
internal object CarateenJson {
    private const val AES_KEY = "7annaba3l_loves_crypto_safe_key!"
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): JsonElement {
        val payload = parse(text)
        val envelope = payload as? JsonObject ?: return payload
        val iv = (envelope["iv"] as? JsonPrimitive)?.contentOrNull
        val encrypted = (envelope["encryptedData"] as? JsonPrimitive)?.contentOrNull
        if (iv == null && encrypted == null) return payload
        require(iv != null && encrypted != null) { "Incomplete Carateen response" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(unhex(iv)),
        )
        return parse(String(cipher.doFinal(unhex(encrypted)), Charsets.UTF_8))
    }

    private fun parse(text: String): JsonElement = json.parseToJsonElement(text).also {
        require(it is JsonObject || it is JsonArray) { "Invalid Carateen response" }
    }

    private fun unhex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex response" }
        return ByteArray(hex.length / 2) {
            hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}
