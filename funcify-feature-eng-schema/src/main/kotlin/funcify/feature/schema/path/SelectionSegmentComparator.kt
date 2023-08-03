package funcify.feature.schema.path

internal object SelectionSegmentComparator : Comparator<SelectionSegment> {

    override fun compare(o1: SelectionSegment?, o2: SelectionSegment?): Int {
        return when (o1) {
            null -> {
                when (o2) {
                    null -> {
                        0
                    }
                    else -> {
                        -1
                    }
                }
            }
            else -> {
                when (o2) {
                    null -> {
                        1
                    }
                    else -> {
                        nonNullSelectionSegmentComparator.compare(o1, o2)
                    }
                }
            }
        }
    }

    private val nonNullSelectionSegmentComparator: Comparator<SelectionSegment> by lazy {
        nonNullSelectionSegmentsComparator()
    }

    private fun nonNullSelectionSegmentsComparator(): Comparator<SelectionSegment> {
        return Comparator.comparing<SelectionSegment, Boolean>(
                FragmentSpread::class::isInstance,
                Boolean::compareTo
            )
            .thenComparing(InlineFragment::class::isInstance, Boolean::compareTo)
            .thenComparing(Field::class::isInstance, Boolean::compareTo)
            .thenComparing(sameTypesComparator())
    }

    private fun sameTypesComparator(): Comparator<SelectionSegment> {
        val fieldComparator: Comparator<Field> = fieldComparator()
        val inlineFragmentComparator: Comparator<InlineFragment> = inlineFragmentComparator()
        val fragmentSpreadComparator: Comparator<FragmentSpread> = fragmentSpreadComparator()
        return Comparator { s1, s2 ->
            when (s1) {
                is Field -> {
                    if (s2 is Field) {
                        fieldComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
                is InlineFragment -> {
                    if (s2 is InlineFragment) {
                        inlineFragmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
                is FragmentSpread -> {
                    if (s2 is FragmentSpread) {
                        fragmentSpreadComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
            }
        }
    }

    private fun fieldComparator(): Comparator<Field> {
        return Comparator.comparing(Field::fieldName)
    }

    private fun inlineFragmentComparator(): Comparator<InlineFragment> {
        return Comparator.comparing(InlineFragment::typeName)
            .thenComparing(InlineFragment::fieldName)
    }

    private fun fragmentSpreadComparator(): Comparator<FragmentSpread> {
        return Comparator.comparing(FragmentSpread::fragmentName)
            .thenComparing(FragmentSpread::typeName)
            .thenComparing(FragmentSpread::fieldName)
    }
}
