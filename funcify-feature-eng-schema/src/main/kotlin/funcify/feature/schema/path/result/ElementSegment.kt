package funcify.feature.schema.path.result

/**
 * @author smccarron
 * @created 2023-10-19
 */
sealed interface ElementSegment : Comparable<ElementSegment> {

    companion object {

        fun comparator(): Comparator<ElementSegment> {
            return ElementSegmentComparator
        }
    }

    override fun compareTo(other: ElementSegment): Int {
        return comparator().compare(this, other)
    }
}
