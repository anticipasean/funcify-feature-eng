package funcify.feature.schema.path.operation

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
                FragmentSpreadSegment::class::isInstance,
                Boolean::compareTo
            )
            .thenComparing(InlineFragmentSegment::class::isInstance, Boolean::compareTo)
            .thenComparing(AliasedFieldSegment::class::isInstance, Boolean::compareTo)
            .thenComparing(FieldSegment::class::isInstance, Boolean::compareTo)
            .thenComparing(sameTypesComparator())
    }

    private fun sameTypesComparator(): Comparator<SelectionSegment> {
        val fieldSegmentComparator: Comparator<FieldSegment> = fieldComparator()
        val aliasedFieldSegmentComparator: Comparator<AliasedFieldSegment> =
            aliasedFieldComparator()
        val inlineFragmentSegmentComparator: Comparator<InlineFragmentSegment> =
            inlineFragmentComparator()
        val fragmentSpreadSegmentComparator: Comparator<FragmentSpreadSegment> =
            fragmentSpreadComparator()
        return Comparator { s1, s2 ->
            when (s1) {
                is FieldSegment -> {
                    if (s2 is FieldSegment) {
                        fieldSegmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
                is AliasedFieldSegment -> {
                    if (s2 is AliasedFieldSegment) {
                        aliasedFieldSegmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
                is InlineFragmentSegment -> {
                    if (s2 is InlineFragmentSegment) {
                        inlineFragmentSegmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
                is FragmentSpreadSegment -> {
                    if (s2 is FragmentSpreadSegment) {
                        fragmentSpreadSegmentComparator.compare(s1, s2)
                    } else {
                        0
                    }
                }
            }
        }
    }

    private fun fieldComparator(): Comparator<FieldSegment> {
        return Comparator.comparing(FieldSegment::fieldName)
    }

    private fun aliasedFieldComparator(): Comparator<AliasedFieldSegment> {
        return Comparator.comparing(AliasedFieldSegment::alias)
            .thenComparing(AliasedFieldSegment::fieldName)
    }

    private fun inlineFragmentComparator(): Comparator<InlineFragmentSegment> {
        return Comparator.comparing(InlineFragmentSegment::typeName)
            .thenComparing(InlineFragmentSegment::selectedField, selectedFieldComparator())
    }

    private fun fragmentSpreadComparator(): Comparator<FragmentSpreadSegment> {
        return Comparator.comparing(FragmentSpreadSegment::fragmentName)
            .thenComparing(FragmentSpreadSegment::typeName)
            .thenComparing(FragmentSpreadSegment::selectedField, selectedFieldComparator())
    }

    private fun selectedFieldComparator(): Comparator<SelectedField> {
        val aliasedFieldSegmentComparator: Comparator<AliasedFieldSegment> =
            aliasedFieldComparator()
        val fieldSegmentComparator: Comparator<FieldSegment> = fieldComparator()
        return Comparator.comparing<SelectedField, Boolean>(
                AliasedFieldSegment::class::isInstance,
                Boolean::compareTo
            )
            .thenComparing(FieldSegment::class::isInstance, Boolean::compareTo)
            .thenComparing { o1, o2 ->
                when (o1) {
                    is AliasedFieldSegment -> {
                        when (o2) {
                            is AliasedFieldSegment -> {
                                aliasedFieldSegmentComparator.compare(o1, o2)
                            }
                            else -> {
                                0
                            }
                        }
                    }
                    is FieldSegment -> {
                        when (o2) {
                            is FieldSegment -> {
                                fieldSegmentComparator.compare(o1, o2)
                            }
                            else -> {
                                0
                            }
                        }
                    }
                }
            }
    }
}
