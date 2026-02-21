package com.materialchat.ui.util

/**
 * Represents a parsed and formatted model name with provider and model parts.
 *
 * @property provider The formatted provider name (e.g., "Antigravity")
 * @property providerAbbreviated An abbreviated version for compact display (e.g., "Antigr...")
 * @property model The formatted model name (e.g., "Claude Opus 4.5")
 * @property modelAbbreviated An abbreviated version for compact display (e.g., "Claude 4.5")
 * @property originalRaw The original raw model identifier
 */
data class ParsedModelName(
    val provider: String,
    val providerAbbreviated: String,
    val model: String,
    val modelAbbreviated: String,
    val originalRaw: String
)

/**
 * Utility object for parsing raw model identifiers into beautifully formatted names.
 *
 * Transforms identifiers like `antigravity/claude-opus-4.5` into structured
 * ParsedModelName objects with formatted provider and model names.
 */
object ModelNameParser {

    /**
     * Brand name corrections for proper capitalization and styling.
     */
    private val brandCorrections = mapOf(
        "openai" to "OpenAI",
        "deepseek" to "DeepSeek",
        "xai" to "xAI",
        "github" to "GitHub",
        "github_copilot" to "GitHub Copilot",
        "anthropic" to "Anthropic",
        "google" to "Google",
        "meta" to "Meta",
        "mistral" to "Mistral",
        "cohere" to "Cohere",
        "perplexity" to "Perplexity",
        "alibaba" to "Alibaba",
        "ollama" to "Ollama",
        "groq" to "Groq",
        "fireworks" to "Fireworks",
        "together" to "Together",
        "anyscale" to "Anyscale",
        "replicate" to "Replicate",
        "huggingface" to "HuggingFace"
    )

    /**
     * Model name acronyms that should be uppercased.
     */
    private val modelAcronyms = setOf("gpt", "llm", "ai", "llama", "phi", "qwen", "yi", "dbrx")

    /**
     * Words that should remain lowercase in model names (unless at start).
     */
    private val lowercaseWords = setOf("and", "or", "the", "a", "an", "for", "with", "to", "of")

    /**
     * Parses a raw model identifier into a structured ParsedModelName.
     *
     * @param rawModelName The raw model identifier (e.g., "antigravity/claude-opus-4.5")
     * @return A ParsedModelName with formatted provider and model names
     */
    fun parse(rawModelName: String): ParsedModelName {
        if (rawModelName.isBlank()) {
            return ParsedModelName(
                provider = "Unknown",
                providerAbbreviated = "Unk",
                model = "Model",
                modelAbbreviated = "Model",
                originalRaw = rawModelName
            )
        }

        val parts = rawModelName.split("/", limit = 2)

        return if (parts.size == 2) {
            // Has provider/model format
            val rawProvider = parts[0]
            val rawModel = parts[1]

            val formattedProvider = formatProviderName(rawProvider)
            val formattedModel = formatModelName(rawModel)

            ParsedModelName(
                provider = formattedProvider,
                providerAbbreviated = abbreviateProvider(formattedProvider),
                model = formattedModel,
                modelAbbreviated = abbreviateModel(formattedModel),
                originalRaw = rawModelName
            )
        } else {
            // Model only (no provider prefix)
            val formattedModel = formatModelName(rawModelName)

            // Try to infer provider from model name
            val inferredProvider = inferProviderFromModel(rawModelName)

            ParsedModelName(
                provider = inferredProvider,
                providerAbbreviated = abbreviateProvider(inferredProvider),
                model = formattedModel,
                modelAbbreviated = abbreviateModel(formattedModel),
                originalRaw = rawModelName
            )
        }
    }

    /**
     * Formats a provider name with proper capitalization and brand corrections.
     */
    private fun formatProviderName(rawProvider: String): String {
        val normalized = rawProvider.lowercase().trim()

        // Check for exact brand correction
        brandCorrections[normalized]?.let { return it }

        // Check for underscore-separated brand correction
        val underscoreNormalized = normalized.replace("-", "_")
        brandCorrections[underscoreNormalized]?.let { return it }

        // Default: replace underscores/hyphens with spaces and title case
        return rawProvider
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { word ->
                brandCorrections[word.lowercase()] ?: word.replaceFirstChar { it.uppercaseChar() }
            }
    }

    /**
     * Formats a model name with proper capitalization, handling versions and acronyms.
     */
    private fun formatModelName(rawModel: String): String {
        // Replace dashes between digits with dots (e.g., "4-6" -> "4.6" for version numbers)
        // then replace remaining separators with spaces
        val spaced = rawModel
            .replace(Regex("""(\d)-(\d)"""), "$1.$2")
            .replace("-", " ")
            .replace("_", " ")

        val words = spaced.split(" ").filter { it.isNotBlank() }

        return words.mapIndexed { index, word ->
            formatModelWord(word, isFirst = index == 0)
        }.joinToString(" ")
    }

    /**
     * Formats a single word in the model name.
     */
    private fun formatModelWord(word: String, isFirst: Boolean): String {
        val lowerWord = word.lowercase()

        // Check if it's an acronym that should be uppercased
        if (modelAcronyms.contains(lowerWord)) {
            return word.uppercase()
        }

        // Check if it's a version number (e.g., "4.5", "v2", "3b")
        if (isVersionLike(word)) {
            return word
        }

        // Check if it's a size indicator (e.g., "70b", "8x7b")
        if (isSizeIndicator(word)) {
            return word.uppercase()
        }

        // Check for lowercase words (unless first word)
        if (!isFirst && lowercaseWords.contains(lowerWord)) {
            return lowerWord
        }

        // Default: title case
        return word.replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Checks if a word looks like a version number.
     */
    private fun isVersionLike(word: String): Boolean {
        return word.matches(Regex("""^[vV]?\d+(\.\d+)*[a-zA-Z]?$"""))
    }

    /**
     * Checks if a word looks like a size indicator (e.g., "70b", "8x7b").
     */
    private fun isSizeIndicator(word: String): Boolean {
        return word.matches(Regex("""^\d+[xX]?\d*[bBmMkK]$"""))
    }

    /**
     * Creates an abbreviated version of the provider name for compact display.
     */
    private fun abbreviateProvider(provider: String): String {
        return if (provider.length > 16) {
            "${provider.take(14)}…"
        } else {
            provider
        }
    }

    /**
     * Creates an abbreviated version of the model name for compact display.
     * Tries to keep the base model name and version while removing middle modifiers.
     */
    private fun abbreviateModel(model: String): String {
        if (model.length <= 18) return model

        val words = model.split(" ")
        if (words.size <= 2) {
            return if (model.length > 18) "${model.take(15)}…" else model
        }

        // Try to keep first word and last word (often version)
        val firstWord = words.first()
        val lastWord = words.last()

        // Check if last word is a version or size
        val abbreviated = if (isVersionLike(lastWord) || isSizeIndicator(lastWord)) {
            "$firstWord $lastWord"
        } else {
            // Just take first two words
            words.take(2).joinToString(" ")
        }

        return if (abbreviated.length > 18) "${abbreviated.take(15)}…" else abbreviated
    }

    /**
     * Tries to infer the provider from the model name.
     */
    private fun inferProviderFromModel(modelName: String): String {
        val lower = modelName.lowercase()
        return when {
            "claude" in lower -> "Anthropic"
            "gpt" in lower || "o1" in lower || "davinci" in lower || "turbo" in lower -> "OpenAI"
            "gemini" in lower || "palm" in lower || "bard" in lower -> "Google"
            "llama" in lower || "codellama" in lower -> "Meta"
            "mistral" in lower || "mixtral" in lower -> "Mistral"
            "deepseek" in lower -> "DeepSeek"
            "qwen" in lower -> "Alibaba"
            "command" in lower || "coral" in lower -> "Cohere"
            "grok" in lower -> "xAI"
            "phi" in lower -> "Microsoft"
            "yi" in lower -> "01.AI"
            else -> "Provider"
        }
    }
}
