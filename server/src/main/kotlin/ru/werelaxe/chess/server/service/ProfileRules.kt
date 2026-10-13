package ru.werelaxe.chess.server.service

/**
 * The rules of usernames and display names (docs/ARCHITECTURE.md section 2.2), shared by
 * registration and the bots created through the admin API. Each check throws a 400 `validation`.
 */
object ProfileRules {
    const val MAX_DISPLAY_NAME_LENGTH = 40
    const val GUEST_PREFIX = "guest-"
    private val USERNAME_PATTERN = Regex("[A-Za-z0-9_]{3,20}")

    /** Literal segments under `/api/players` and `/api/auth` that would shadow a profile of that name. */
    private val RESERVED_USERNAMES = setOf("leaderboard", "me", "bots")
    private val INVISIBLE_CATEGORIES = setOf(Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR)

    fun validateUsername(username: String) {
        // Checked before the pattern so that the reason is the reservation, not the hyphen.
        if (username.startsWith(GUEST_PREFIX, ignoreCase = true)) {
            throw ApiException.validation("Names starting with $GUEST_PREFIX are reserved")
        }
        if (username.lowercase() in RESERVED_USERNAMES) {
            throw ApiException.validation("The name '$username' is reserved")
        }
        if (!USERNAME_PATTERN.matches(username)) {
            throw ApiException.validation("Username must be 3-20 characters: letters, digits or underscore")
        }
    }

    /**
     * A display name is shown wherever the username is, so it must be visible and harmless:
     * no control characters (PostgreSQL rejects NUL outright), no formatting characters such as
     * zero-width spaces or bidirectional overrides, no line or paragraph separators, and at
     * least one letter or digit so that it cannot be blank once rendered. [name] is expected trimmed.
     */
    fun validateDisplayName(name: String) {
        if (name.isEmpty() || name.length > MAX_DISPLAY_NAME_LENGTH) {
            throw ApiException.validation("Display name must be 1-$MAX_DISPLAY_NAME_LENGTH characters, or null to clear it")
        }
        val codePoints = name.codePoints().toArray()
        if (codePoints.any { isInvisible(it) }) {
            throw ApiException.validation("Display name must not contain control or formatting characters")
        }
        if (codePoints.none { Character.isLetterOrDigit(it) }) {
            throw ApiException.validation("Display name must contain at least one letter or digit")
        }
    }

    /** Control characters and the categories Cf, Zl and Zp (U+200B..U+200F, U+202A..U+202E, U+2066..U+2069 are Cf). */
    private fun isInvisible(codePoint: Int): Boolean =
        Character.isISOControl(codePoint) || Character.getType(codePoint).toByte() in INVISIBLE_CATEGORIES
}
