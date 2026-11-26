package mihon.domain.migration.usecases

/** Returns a new tag list only if the source contributes at least one new tag. */
internal fun mergeMigrationTags(source: List<String>?, target: List<String>?): List<String>? {
    if (source.isNullOrEmpty()) return null

    val existingTags = target.orEmpty()
    val knownTags = existingTags.mapTo(HashSet()) { it.trim().lowercase() }
    val newTags = source.map(String::trim).filter { it.isNotEmpty() && knownTags.add(it.lowercase()) }
    return if (newTags.isEmpty()) null else existingTags + newTags
}
