package tachiyomi.domain.category.model

data class CategoryParentChange(val parentId: Long?, val updatedAt: Long)

/**
 * Categories in their stored order. Hidden or omitted ancestors do not hide otherwise visible rows.
 */
class CategoryTree(private val categories: List<Category>) {
    private val byId = categories.associateBy(Category::id)

    fun pathTo(categoryId: Long): List<Category> {
        val visited = HashSet<Long>()
        return buildList {
            var category = byId[categoryId]
            while (category != null && visited.add(category.id)) {
                add(category)
                category = category.parentId?.let(byId::get)
            }
        }.asReversed()
    }

    fun inHierarchyOrder(): List<Category> = buildList {
        val children = categories.groupBy { it.parentId?.takeIf(byId::containsKey) }
        val visited = HashSet<Long>()
        val pending = ArrayDeque<Category>()

        fun appendBranch(root: Category) {
            pending.addLast(root)
            while (pending.isNotEmpty()) {
                val category = pending.removeLast()
                if (visited.add(category.id)) {
                    add(category)
                    children[category.id].orEmpty().asReversed().forEach(pending::addLast)
                }
            }
        }

        children[null].orEmpty().forEach(::appendBranch)
        categories.forEach(::appendBranch)
    }

    fun possibleParents(categoryId: Long): List<Category> =
        inHierarchyOrder().filter { candidate ->
            !candidate.isSystemCategory && pathTo(candidate.id).none { it.id == categoryId }
        }

    /**
     * Validates the requested final state together so a batch can safely reverse a relationship.
     * Changes leading to a cycle are ignored. Other branches can still be restored.
     */
    fun validParentChanges(requested: Map<Long, CategoryParentChange>): Map<Long, CategoryParentChange> {
        val proposed = requested.filter { (categoryId, change) ->
            val category = byId[categoryId]
            val parentId = change.parentId
            category != null && !category.isSystemCategory && change.updatedAt >= category.parentUpdatedAt &&
                (category.parentId != parentId || change.updatedAt > category.parentUpdatedAt) &&
                (parentId == null || (parentId != categoryId && byId[parentId]?.isSystemCategory == false))
        }
        val finalParents = categories.associate { it.id to it.parentId } + proposed.mapValues { it.value.parentId }
        return proposed.filterKeys { categoryId ->
            val visited = HashSet<Long>()
            var current: Long? = categoryId
            while (current != null && visited.add(current)) {
                current = finalParents[current]
            }
            current == null
        }
    }
}

fun List<Category>.moveCategoryAmongSiblings(categoryId: Long, targetId: Long): List<Category> {
    val from = indexOfFirst { it.id == categoryId }
    val to = indexOfFirst { it.id == targetId }
    if (from < 0 || to < 0 || from == to || this[from].parentId != this[to].parentId) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
