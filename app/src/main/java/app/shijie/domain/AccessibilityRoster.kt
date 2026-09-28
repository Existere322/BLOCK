package app.shijie.domain

/**
 * Parses `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
 * The platform stores a colon-separated list, and each entry is either
 * `package/class` or the short form `package/.RelativeClass`.
 */
object AccessibilityRoster {
    fun listed(raw: String?, packageName: String, className: String): Boolean {
        return entries(raw).any { same(it, packageName, className) }
    }

    /** Appends [flatName] without dropping or duplicating other services. */
    fun ensure(raw: String?, flatName: String): String {
        val packageName = flatName.substringBefore('/')
        val className = flatName.substringAfter('/', "")
        val kept = entries(raw).filterNot { same(it, packageName, className) }
        return (kept + flatName).joinToString(":")
    }

    fun remove(raw: String?, packageName: String, className: String): String {
        return entries(raw).filterNot { same(it, packageName, className) }.joinToString(":")
    }

    private fun entries(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun same(entry: String, packageName: String, className: String): Boolean {
        val slash = entry.indexOf('/')
        if (slash <= 0 || slash == entry.lastIndex) return false
        val pkg = entry.substring(0, slash)
        if (!pkg.equals(packageName, ignoreCase = true)) return false
        val cls = entry.substring(slash + 1)
        val normalized = if (cls.startsWith(".")) packageName + cls else cls
        return normalized.equals(className, ignoreCase = true)
    }
}
