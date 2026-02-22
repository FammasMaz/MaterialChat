package com.materialchat.domain.model

/**
 * Represents the possible voting outcomes in an arena battle.
 */
enum class ArenaVote {
    /** The left model produced a better response. */
    LEFT,

    /** The right model produced a better response. */
    RIGHT,

    /** Both models produced equally good responses. */
    TIE,

    /** Both models produced poor responses. */
    BOTH_BAD
}
