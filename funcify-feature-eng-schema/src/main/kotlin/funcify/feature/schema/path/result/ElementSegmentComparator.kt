package funcify.feature.schema.path.result

internal object ElementSegmentComparator : Comparator<ElementSegment> {

    override fun compare(o1: ElementSegment?, o2: ElementSegment?): Int {
        return when (o1) {
            null -> {
                when (o2) {
                    null -> 0
                    else -> 1
                }
            }
            else -> {
                when (o2) {
                    null -> -1
                    else -> nonNullElementSegmentComparator.compare(o1, o2)
                }
            }
        }
    }

    private val nonNullElementSegmentComparator: Comparator<ElementSegment> by lazy {
        nonNullElementSegmentComparator()
    }

    private fun nonNullElementSegmentComparator(): Comparator<ElementSegment> {
        return Comparator.comparing<ElementSegment, Boolean>(
                NamedListSegment::class::isInstance,
                Boolean::compareTo
            )
            .thenComparing(NamedSegment::class::isInstance, Boolean::compareTo)
            .thenComparing(UnnamedListSegment::class::isInstance, Boolean::compareTo)
            .thenComparing(sameTypesComparator())
    }

    private fun sameTypesComparator(): Comparator<ElementSegment> {
        val unnamedListSegmentComparator: Comparator<UnnamedListSegment> =
            unnamedListSegmentComparator()
        val namedListSegmentComparator: Comparator<NamedListSegment> = namedListSegmentComparator()
        val namedSegmentComparator: Comparator<NamedSegment> = namedSegmentComparator()
        return Comparator { s1, s2 ->
            when (s1) {
                is UnnamedListSegment -> {
                    if (s2 is UnnamedListSegment) {
                        unnamedListSegmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
                is NamedListSegment -> {
                    if (s2 is NamedListSegment) {
                        namedListSegmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
                is NamedSegment -> {
                    if (s2 is NamedSegment) {
                        namedSegmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
            }
        }
    }

    private fun namedSegmentComparator(): Comparator<NamedSegment> {
        return Comparator.comparing(NamedSegment::name)
    }

    private fun namedListSegmentComparator(): Comparator<NamedListSegment> {
        return Comparator.comparing(NamedListSegment::name).thenComparing(NamedListSegment::index)
    }

    private fun unnamedListSegmentComparator(): Comparator<UnnamedListSegment> {
        return Comparator.comparing(UnnamedListSegment::index)
    }
}
