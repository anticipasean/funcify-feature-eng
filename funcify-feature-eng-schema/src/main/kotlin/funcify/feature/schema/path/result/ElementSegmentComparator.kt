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
        return namesComparator()
            .thenComparing(ListSegment::class::isInstance, Boolean::compareTo)
            .thenComparing(NameSegment::class::isInstance, Boolean::compareTo)
            .thenComparing(sameTypesComparator())
    }

    private fun namesComparator(): Comparator<ElementSegment> {
        return Comparator { es1, es2 ->
            when (es1) {
                is ListSegment -> {
                    when (es2) {
                        is ListSegment -> {
                            es1.name.compareTo(es2.name)
                        }
                        is NameSegment -> {
                            es1.name.compareTo(es2.name)
                        }
                    }
                }
                is NameSegment -> {
                    when (es2) {
                        is ListSegment -> {
                            es1.name.compareTo(es2.name)
                        }
                        is NameSegment -> {
                            es1.name.compareTo(es2.name)
                        }
                    }
                }
            }
        }
    }

    private fun sameTypesComparator(): Comparator<ElementSegment> {
        val listSegmentComparator: Comparator<ListSegment> = listSegmentComparator()
        val nameSegmentComparator: Comparator<NameSegment> = nameSegmentComparator()
        return Comparator { s1, s2 ->
            when (s1) {
                is ListSegment -> {
                    if (s2 is ListSegment) {
                        listSegmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
                is NameSegment -> {
                    if (s2 is NameSegment) {
                        nameSegmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
            }
        }
    }

    private fun nameSegmentComparator(): Comparator<NameSegment> {
        return Comparator.comparing(NameSegment::name)
    }

    private fun listSegmentComparator(): Comparator<ListSegment> {
        return Comparator.comparing(ListSegment::name)
            .thenComparing(ListSegment::indices, listComparator())
    }

    private fun <T : Comparable<T>> listComparator(): Comparator<List<T>> {
        return Comparator { l1, l2 ->
            val sizeComparisonResult: Int = l1.size.compareTo(l2.size)
            l1.zip(l2) { t1, t2 -> t1.compareTo(t2) }
                .firstOrNull { comparisonResult: Int -> comparisonResult != 0 }
                ?: sizeComparisonResult
        }
    }
}
