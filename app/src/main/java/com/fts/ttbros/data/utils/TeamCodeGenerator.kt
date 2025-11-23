package com.fts.ttbros.data.utils

import kotlin.random.Random

object TeamCodeGenerator {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 6

    fun generate(): String {
        return buildString {
            repeat(CODE_LENGTH) {
                append(ALPHABET[Random.nextInt(ALPHABET.length)])
            }
        }
    }
}

