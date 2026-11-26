package eu.kanade.domain.source.interactor

internal fun <T> List<T>.filterSourcesByName(query: String?, name: (T) -> String): List<T> {
    val term = query?.trim().orEmpty()
    if (term.isEmpty()) return this

    return filter { name(it).contains(term, ignoreCase = true) }
}
