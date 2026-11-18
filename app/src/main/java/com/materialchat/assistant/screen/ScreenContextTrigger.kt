package com.materialchat.assistant.screen

import java.util.Locale

/**
 * The cheap local test for "is this question about what is on the screen?", run on the typed
 * or recognised utterance before any model round trip.
 *
 * Two word lists, no regex soup: fixed phrases that only make sense with a screen in front of
 * the user ("on my screen", "what am I looking at", "translate this") and a deictic rule, a
 * pointing word ("this", "that") followed by something that is on a screen ("tweet", "page",
 * "message"), optionally with one qualifier in between ("this first tweet"). A miss here is
 * not final: the model can still ask for the capture through a directive (see
 * [ScreenContextPrompt]); a hit only stages the capture, which the user can remove.
 */
object ScreenContextTrigger {
    private val PHRASES = listOf(
        "on my screen", "on the screen", "on screen", "my screen", "this screen", "the screen",
        "what am i looking at", "what i'm looking at", "what i am looking at",
        "what is this", "what's this", "whats this", "what is that", "what's that", "whats that",
        "what does this", "what does that", "what does it say", "what is it saying", "what's it say",
        "what is it about", "what's it about", "what is written", "what's written",
        "read this", "read that", "read it", "read the screen", "read it to me", "read this to me",
        "translate this", "translate that", "translate it", "translate the screen",
        "summarize this", "summarise this", "summarize that", "summarise that", "summarize the screen",
        "summarise the screen", "explain this", "explain that", "describe this", "describe that",
        "who is this", "who's this", "who is that", "who's that", "where is this", "where's this",
        "how much is this", "how much is that", "is this true", "is that true", "is this real",
        "is this legit", "is this a scam", "is that a scam", "reply to this", "respond to this",
        "answer this", "help me with this", "help with this", "what's on", "what is on",
        "look at this", "look at that", "take a look", "can you see", "do you see"
    )

    private val DEICTIC = setOf("this", "that", "these", "those")

    /** One word that may sit between the pointing word and the thing: "this first tweet". */
    private val QUALIFIERS = setOf(
        "first", "second", "third", "last", "top", "bottom", "whole", "entire", "new", "latest",
        "long", "short", "highlighted", "selected", "open", "current", "little", "big", "red", "weird",
        "strange", "other", "next", "previous", "one"
    )

    private val SUBJECTS = setOf(
        "tweet", "tweets", "post", "posts", "message", "messages", "page", "email", "emails", "mail",
        "text", "texts", "article", "screen", "screenshot", "photo", "picture", "image", "video",
        "comment", "comments", "thread", "chat", "conversation", "notification", "error", "code",
        "recipe", "product", "listing", "ad", "advert", "review", "reviews", "headline", "story",
        "news", "map", "chart", "graph", "table", "document", "pdf", "form", "website", "site",
        "app", "menu", "dialog", "popup", "pop-up", "paragraph", "sentence", "word", "line",
        "question", "quiz", "problem", "equation", "math", "song", "lyrics", "price", "offer", "deal",
        "invoice", "receipt", "bill", "ticket", "event", "address", "number", "link", "button",
        "setting", "settings", "option", "term", "terms", "contract", "clause", "warning", "alert",
        "result", "results", "person", "guy", "actor", "player", "place", "building", "plant",
        "animal", "thing", "stuff", "item", "one", "meme", "joke", "quote", "tweet's", "post's"
    )

    private val NON_WORD = Regex("[^\\p{L}\\p{N}' -]")
    private val SPACES = Regex("\\s+")

    /** True when [utterance] refers to what is on the screen. */
    fun matches(utterance: String): Boolean {
        val text = normalise(utterance)
        if (text.isEmpty()) return false
        val padded = " $text "
        if (PHRASES.any { padded.contains(" $it ") }) return true
        val tokens = text.split(' ')
        for (index in tokens.indices) {
            if (tokens[index] !in DEICTIC) continue
            val next = tokens.getOrNull(index + 1) ?: continue
            if (next in SUBJECTS) return true
            if (next in QUALIFIERS && tokens.getOrNull(index + 2)?.let { it in SUBJECTS } == true) return true
        }
        return false
    }

    private fun normalise(input: String): String =
        input.lowercase(Locale.ROOT)
            .replace('\u2019', '\'')
            .replace(NON_WORD, " ")
            .replace(SPACES, " ")
            .trim()
}
