package com.materialchat.assistant.voice

/**
 * Converts append-only cumulative model snapshots into non-repeating speech chunks.
 * A revised snapshot is rejected: callers must [reset] for a new response.
 * Complete prose sentences are emitted before newline; uncertain Markdown stays buffered.
 */
class StreamingSpeechChunker {
    private var snapshot = ""
    private var pendingLine = ""
    private var pendingSpeech = ""
    private var fence: Char? = null
    private var fenceLength = 0
    private var complete = false

    fun update(content: String, isFinal: Boolean = false): List<String> {
        check(!complete || content == snapshot) { "Reset before starting another response" }
        require(content.startsWith(snapshot)) { "Speech snapshots must be append-only" }
        if (complete) return emptyList()
        pendingLine += content.substring(snapshot.length)
        snapshot = content
        val chunks = mutableListOf<String>()
        while ('\n' in pendingLine) {
            val end = pendingLine.indexOf('\n')
            consumeLine(pendingLine.substring(0, end), chunks)
            pendingLine = pendingLine.substring(end + 1)
        }
        // A paragraph need not contain a newline. Consume only a safe prefix ending
        // in sentence punctuation followed by whitespace; leave partial markup intact.
        if (!isFinal && fence == null && pendingLine.isNotBlank()) {
            val safeEnd = Regex("[.!?]+\\s+").findAll(pendingLine)
                .map { it.range.last + 1 }
                .lastOrNull { end ->
                    val prefix = pendingLine.substring(0, end)
                    val trimmed = prefix.trimStart()
                    !trimmed.startsWith("`") && !trimmed.startsWith("~") &&
                        !prefix.contains('`') && !prefix.contains('[') &&
                        !prefix.contains('<') && !prefix.contains("http") &&
                        !prefix.startsWith("    ") && !prefix.startsWith('\t') &&
                        !trimmed.startsWith('|')
                }
            if (safeEnd != null) {
                consumeLine(pendingLine.substring(0, safeEnd), chunks)
                pendingLine = pendingLine.substring(safeEnd)
            }
        }
        if (isFinal) {
            consumeLine(pendingLine, chunks)
            pendingLine = ""
            if (pendingSpeech.isNotBlank()) chunks += pendingSpeech.trim()
            pendingSpeech = ""
            complete = true
        }
        return chunks
    }

    fun reset() {
        snapshot = ""
        pendingLine = ""
        pendingSpeech = ""
        fence = null
        fenceLength = 0
        complete = false
    }

    private fun consumeLine(line: String, chunks: MutableList<String>) {
        val trimmed = line.trimStart()
        val marker = trimmed.firstOrNull()
        val count = if (marker == '`' || marker == '~') trimmed.takeWhile { it == marker }.length else 0
        if (fence != null) {
            if (marker == fence && count >= fenceLength && trimmed.drop(count).isBlank()) fence = null
            return
        }
        if (count >= 3) {
            fence = marker
            fenceLength = count
            return
        }
        // Indented code, tables and thematic breaks are not useful spoken prose.
        if (line.startsWith("    ") || line.startsWith('\t') || trimmed.startsWith('|') ||
            trimmed.matches(Regex("([-*_]\\s*){3,}"))) return
        val prose = line
            .replace(Regex("`+[^`]*`+"), " ")
            .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("^\\s{0,3}(?:#{1,6}\\s+|>\\s*|[-+*]\\s+|\\d+[.)]\\s+)"), "")
            .replace(Regex("[*_~]"), "")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (prose.isEmpty()) return
        pendingSpeech = (pendingSpeech + " " + prose).trimStart()
        val boundaries = Regex("[.!?]+(?=\\s|$)").findAll(pendingSpeech).toList()
        var consumed = 0
        for (boundary in boundaries) {
            val end = boundary.range.last + 1
            chunks += pendingSpeech.substring(consumed, end).trim()
            consumed = end
        }
        pendingSpeech = pendingSpeech.substring(consumed).trimStart()
    }
}
