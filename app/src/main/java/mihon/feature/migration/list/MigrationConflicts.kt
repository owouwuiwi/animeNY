package mihon.feature.migration.list

internal fun findMigrationConflicts(destinations: Map<Long, Long?>): Set<Long> {
    val destinationCounts = destinations.values.filterNotNull().groupingBy { it }.eachCount()
    return buildSet {
        destinations.forEach { (origin, destination) ->
            if (destination == null) return@forEach
            if (destinationCounts.getValue(destination) > 1) add(origin)
            if (destination in destinations) {
                add(origin)
                add(destination)
            }
        }
    }
}
