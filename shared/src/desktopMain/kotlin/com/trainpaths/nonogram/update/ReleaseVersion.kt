package com.trainpaths.nonogram.update

/** The tag `release-desktop.yml` publishes, e.g. `desktop-v1.1.42`. */
private const val TAG_PREFIX = "desktop-v"

fun parseDesktopTag(tag: String): String? {
    if (!tag.startsWith(TAG_PREFIX)) return null
    val version = tag.removePrefix(TAG_PREFIX)
    val parts = version.split('.')
    if (parts.any { part -> part.isEmpty() || !part.all(Char::isDigit) }) return null
    return version
}

fun isNewer(remote: String, local: String): Boolean {
    val remoteParts = remote.split('.').map { it.toIntOrNull() ?: return false }
    val localParts = local.split('.').map { it.toIntOrNull() ?: return false }
    repeat(maxOf(remoteParts.size, localParts.size)) { index ->
        val r = remoteParts.getOrElse(index) { 0 }
        val l = localParts.getOrElse(index) { 0 }
        if (r != l) return r > l
    }
    return false
}
