package com.materialchat.domain.model

import java.util.UUID

/**
 * Domain model representing a custom AI persona.
 *
 * A persona provides a pre-configured personality and expertise context
 * that shapes the AI's behaviour through a tailored system prompt,
 * tone of voice, and topical expertise tags.
 *
 * @property id Unique identifier
 * @property name Display name of the persona
 * @property emoji Emoji avatar for the persona
 * @property description Short human-readable description
 * @property systemPrompt The system prompt injected when this persona is active
 * @property expertiseTags Topical tags describing the persona's strengths
 * @property tone Communication tone (e.g. PROFESSIONAL, CREATIVE)
 * @property conversationStarters Suggested first-message prompts
 * @property colorSeed Seed value for deterministic colour generation
 * @property isBuiltin Whether this persona is a built-in default
 * @property createdAt Epoch milliseconds when the persona was created
 * @property updatedAt Epoch milliseconds when the persona was last modified
 */
data class Persona(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "\uD83E\uDD16",
    val description: String = "",
    val systemPrompt: String,
    val expertiseTags: List<String> = emptyList(),
    val tone: PersonaTone = PersonaTone.BALANCED,
    val conversationStarters: List<String> = emptyList(),
    val colorSeed: Int? = null,
    val isBuiltin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
