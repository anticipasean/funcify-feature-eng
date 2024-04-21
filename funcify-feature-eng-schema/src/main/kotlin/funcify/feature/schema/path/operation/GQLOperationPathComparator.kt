package funcify.feature.schema.path.operation

import arrow.core.Option

internal object GQLOperationPathComparator : Comparator<GQLOperationPath> {

    override fun compare(sp1: GQLOperationPath?, sp2: GQLOperationPath?): Int {
        return when (sp1) {
            null -> {
                when (sp2) {
                    null -> {
                        0
                    }
                    else -> {
                        -1
                    }
                }
            }
            else -> {
                when (sp2) {
                    null -> {
                        1
                    }
                    else -> {
                        gqlOperationPathComparator.compare(sp1, sp2)
                    }
                }
            }
        }
    }

    private val gqlOperationPathComparator: Comparator<GQLOperationPath> by lazy {
        Comparator.comparing(GQLOperationPath::scheme, String::compareTo)
            .thenComparing(GQLOperationPath::selection, GQLOperationPathComparator::compareLists)
            .thenComparing(
                GQLOperationPath::argument,
                GQLOperationPathComparator::compareNamePathPairOptions
            )
            .thenComparing(
                GQLOperationPath::directive,
                GQLOperationPathComparator::compareNamePathPairOptions
            )
    }

    private fun <T : Comparable<T>> compareLists(l1: List<T>, l2: List<T>): Int {
        return when (val sizeComparison: Int = l1.size.compareTo(l2.size)) {
            0 -> {
                l1.asSequence()
                    .zip(l2.asSequence()) { t1: T, t2: T -> t1.compareTo(t2) }
                    .firstOrNull { comparison: Int -> comparison != 0 } ?: 0
            }
            else -> {
                sizeComparison
            }
        }
    }

    private fun <T : Comparable<T>> compareNamePathPairOptions(
        op1: Option<Pair<T, List<T>>>,
        op2: Option<Pair<T, List<T>>>
    ): Int {
        return when (val p1: Pair<T, List<T>>? = op1.orNull()) {
            null -> {
                when (op2.orNull()) {
                    null -> {
                        0
                    }
                    else -> {
                        -1
                    }
                }
            }
            else -> {
                when (val p2: Pair<T, List<T>>? = op2.orNull()) {
                    null -> {
                        1
                    }
                    else -> {
                        when (val keyComparison: Int = p1.first.compareTo(p2.first)) {
                            0 -> {
                                compareLists(p1.second, p2.second)
                            }
                            else -> {
                                keyComparison
                            }
                        }
                    }
                }
            }
        }
    }
}
