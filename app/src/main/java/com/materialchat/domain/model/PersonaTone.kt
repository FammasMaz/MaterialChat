package com.materialchat.domain.model

/**
 * Defines the communication tone for an AI persona.
 *
 * Each tone influences how the persona frames responses,
 * affecting vocabulary, formality, and engagement style.
 */
enum class PersonaTone {
    FRIENDLY,
    PROFESSIONAL,
    CASUAL,
    ACADEMIC,
    CREATIVE,
    BALANCED;

    companion object {
        /**
         * Parses a tone string (case-insensitive) to a [PersonaTone].
         * Falls back to [BALANCED] for unrecognized values.
         */
        fun fromString(value: String): PersonaTone =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BALANCED
    }
}
