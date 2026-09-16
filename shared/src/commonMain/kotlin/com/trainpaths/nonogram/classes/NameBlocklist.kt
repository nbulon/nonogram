package com.trainpaths.nonogram.classes

/**
 * Terms a puzzle name may not contain, matched as substrings of the folded name (see [containsBlockedTerm]).
 * Substring matching is what defeats spacing and punctuation tricks, so every entry is at least four letters
 * and chosen not to occur inside ordinary words.
 */
internal val BLOCKED_NAME_TERMS: Set<String> = setOf(
    "fuck",
    "shit",
    "cunt",
    "bitch",
    "asshole",
    "arsehole",
    "bastard",
    "dickhead",
    "wanker",
    "whore",
    "slut",
    "nigger",
    "nigga",
    "faggot",
    "retard",
    "kike",
    "chink",
    "tranny",
    "hitler",
    "rapist",
)

private val LEET = mapOf(
    '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's',
)

/** Lowercased, leetspeak undone and everything but a–z dropped, so "S.h 1 t" folds to "shit". */
private fun foldName(name: String): String = buildString {
    for (char in name.lowercase()) {
        val folded = LEET[char] ?: char
        if (folded in 'a'..'z') append(folded)
    }
}

internal fun containsBlockedTerm(name: String): Boolean {
    val folded = foldName(name)
    return BLOCKED_NAME_TERMS.any { it in folded }
}
