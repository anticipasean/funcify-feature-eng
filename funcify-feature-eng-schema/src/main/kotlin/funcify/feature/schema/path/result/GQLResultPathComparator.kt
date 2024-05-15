package funcify.feature.schema.path.result

/**
 * @author smccarron
 * @created 2023-10-19
 */
internal object GQLResultPathComparator : Comparator<GQLResultPath> {

    override fun compare(o1: GQLResultPath?, o2: GQLResultPath?): Int {
        return when (o1) {
            null -> {
                when (o2) {
                    null -> 0
                    else -> -1
                }
            }
            else -> {
                when (o2) {
                    null -> 1
                    else -> compareNonNullPaths(o1, o2)
                }
            }
        }
    }

    private fun compareNonNullPaths(p1: GQLResultPath, p2: GQLResultPath): Int {
        return when (val schemeComp: Int = p1.scheme.compareTo(p2.scheme)) {
            0 -> {
                comparePathLists(p1.elementSegments, p2.elementSegments)
            }
            else -> {
                schemeComp
            }
        }
    }

    private fun comparePathLists(ps1: List<ElementSegment>, ps2: List<ElementSegment>): Int {
        val sizeComparisonResult: Int = ps1.size.compareTo(ps2.size)
        return ps1.asSequence()
            .zip(ps2.asSequence()) { s1: ElementSegment, s2: ElementSegment -> s1.compareTo(s2) }
            .firstOrNull { segmentComparisonResult: Int -> segmentComparisonResult != 0 }
            ?: sizeComparisonResult
    }
}
