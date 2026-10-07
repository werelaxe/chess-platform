package ru.werelaxe.chess.server.service

import java.security.SecureRandom

/** 12 random base62 characters; unguessable so that private games can be shared by link. */
class GameIdGenerator(private val length: Int = 12) {
    private val random = SecureRandom()

    fun next(): String {
        val chars = CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return String(chars)
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }
}
