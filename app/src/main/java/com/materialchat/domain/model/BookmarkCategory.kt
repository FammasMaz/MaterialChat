package com.materialchat.domain.model

/**
 * Categories for organizing bookmarked messages in the knowledge base.
 *
 * Each category has a human-readable display name and an icon identifier
 * for rendering in the UI layer.
 *
 * @property displayName Human-readable name shown in the UI
 * @property iconName Identifier for the Material icon to use
 */
enum class BookmarkCategory(val displayName: String, val iconName: String) {
    CODE("Code", "code"),
    EXPLANATION("Explanation", "school"),
    IDEA("Idea", "lightbulb"),
    REFERENCE("Reference", "menu_book"),
    HOWTO("How-To", "build"),
    GENERAL("General", "bookmark_border")
}
