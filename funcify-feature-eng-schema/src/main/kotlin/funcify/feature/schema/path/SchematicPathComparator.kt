package funcify.feature.schema.path

import arrow.core.Option

internal object SchematicPathComparator : Comparator<SchematicPath> {

    override fun compare(sp1: SchematicPath?, sp2: SchematicPath?): Int {
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
                        schematicPathComparator.compare(sp1, sp2)
                    }
                }
            }
        }
    }

    private val schematicPathComparator: Comparator<SchematicPath> by lazy {
        Comparator.comparing(SchematicPath::scheme, String::compareTo)
            .thenComparing(SchematicPath::pathSegments, stringListComparator)
            .thenComparing(SchematicPath::argument, namedPathPairComparator)
            .thenComparing(SchematicPath::directive, namedPathPairComparator)
    }

    private val stringListComparator: Comparator<List<String>> by lazy {
        Comparator<List<String>> { l1, l2 -> //
            l1.asSequence()
                .zip(l2.asSequence()) { s1: String, s2: String -> s1.compareTo(s2) }
                .firstOrNull { comparison: Int -> comparison != 0 }
                ?: l1.size.compareTo(l2.size)
        }
    }

    private val namedPathPairComparator: Comparator<Option<Pair<String, List<String>>>> by lazy {
        Comparator<Option<Pair<String, List<String>>>> { pairOpt1, pairOpt2 -> //
            when (val p1: Pair<String, List<String>>? = pairOpt1.orNull()) {
                null -> {
                    when (pairOpt2.orNull()) {
                        null -> {
                            0
                        }
                        else -> {
                            -1
                        }
                    }
                }
                else -> {
                    when (val p2: Pair<String, List<String>>? = pairOpt2.orNull()) {
                        null -> {
                            1
                        }
                        else -> {
                            when (val keyComparison: Int = p1.first.compareTo(p2.first)) {
                                0 -> {
                                    stringListComparator.compare(p1.second, p2.second)
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
}
