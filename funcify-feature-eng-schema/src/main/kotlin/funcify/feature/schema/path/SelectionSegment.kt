package funcify.feature.schema.path

/**
 * @author smccarron
 * @created 2023-08-02
 */
sealed interface SelectionSegment : Comparable<SelectionSegment> {

    companion object {

        fun comparator(): Comparator<SelectionSegment> {
            return SelectionSegmentComparator
        }
    }

    override fun compareTo(other: SelectionSegment): Int {
        return comparator().compare(this, other)
    }
}
