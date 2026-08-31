package com.materialchat.domain.util

/**
 * Canonical model identity across providers.
 *
 * The same underlying model is served by many providers under different ids
 * (e.g. Z.ai's GLM-5.3-Flash appears as "z-ai/glm-5.3-flash", "opencode/
 * x-preview-f-free" — the stealth preview codename "ox-alpha" — and other
 * provider-specific spellings). Stats (Insights) and the Arena leaderboard
 * must aggregate these under ONE identity instead of counting them separately.
 *
 * The canonical key is the provider-neutral model name: the last path
 * segment of the id, lowercased, with common provider prefixes and
 * whitespace/underscore variants normalized. Known stealth/preview codenames
 * are mapped to their revealed real model via [ALIASES] (evidence:
 * Z.ai open-sourced "Ox Alpha" as GLM-5.3-Flash, Aug 2026).
 */
object ModelIdentity {

    /** Provider-prefixes stripped before comparison (OpenRouter-style namespaces). */
    private val PROVIDER_PREFIXES = listOf(
        "z-ai/", "zai/", "openrouter/", "opencode/", "openai/", "anthropic/",
        "google/", "mistralai/", "meta-llama/", "deepseek/", "qwen/", "stealth/"
    )

    /** Stealth/preview codenames mapped to their revealed real model id. */
    private val ALIASES: Map<String, String> = mapOf(
        // "Ox Alpha" was OpenCode Zen's stealth preview (x-preview-f-free);
        // Z.ai identified it as GLM-5.3-Flash when open-sourcing it (Aug 2026).
        "ox-alpha" to "glm-5.3-flash",
        "x-preview-f" to "glm-5.3-flash",
        "x-preview-f-free" to "glm-5.3-flash"
    )

    /**
     * Canonical key for a model id: lowercased last path segment after
     * provider-prefix stripping, with underscores treated as hyphens.
     * Unmapped stealth codenames resolve to themselves (they stay separate
     * until revealed — a negative finding is preserved, not guessed).
     */
    fun canonicalKey(modelName: String?): String? {
        if (modelName.isNullOrBlank()) return null
        var key = modelName.trim().lowercase()
        for (prefix in PROVIDER_PREFIXES) {
            if (key.startsWith(prefix)) {
                key = key.removePrefix(prefix)
                break
            }
        }
        key = key.substringAfterLast('/')
        // Aliases are checked against the bare name AND the prefixed form.
        ALIASES[key]?.let { return it }
        ALIASES[modelName.trim().lowercase()]?.let { return it }
        return key.replace('_', '-')
    }

    /**
     * True when both ids denote the same underlying model.
     */
    fun sameModel(a: String?, b: String?): Boolean {
        val ka = canonicalKey(a) ?: return false
        val kb = canonicalKey(b) ?: return false
        return ka == kb
    }

    /**
     * Display name for a canonical key: the key itself (already normalized).
     */
    fun displayName(canonicalKey: String): String = canonicalKey
}
