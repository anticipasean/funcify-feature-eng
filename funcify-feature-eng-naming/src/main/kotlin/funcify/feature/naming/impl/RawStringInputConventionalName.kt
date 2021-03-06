package funcify.feature.naming.impl

import funcify.feature.naming.ConventionalName
import funcify.feature.naming.NameSegment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
data class RawStringInputConventionalName(override val namingConventionKey: Any,
                                          private val rawStringNameSegments: List<String>,
                                          override val delimiter: String = ConventionalName.EMPTY_STRING_DELIMITER) : ConventionalName {

    init {
        if (rawStringNameSegments.isEmpty()) {
            throw IllegalArgumentException("must have at least one name component")
        }
    }

    override val nameSegments: ImmutableList<NameSegment> by lazy {
        rawStringNameSegments.asSequence()
                .flatMap { s -> // since name components cannot contain the delimiter
                    // if the delimiter is non-empty, then split based on containment
                    if (delimiter.isNotEmpty() && s.contains(delimiter)) {
                        s.splitToSequence(delimiter)
                    } else {
                        sequenceOf(s)
                    }
                }
                .map { s -> DefaultNameSegment(s) }
                .fold(persistentListOf()) { acc: PersistentList<NameSegment>, nc: DefaultNameSegment ->
                    acc.add(nc)
                }
    }

    override val qualifiedForm: String by lazy { super.qualifiedForm }
    override val encodedUriForm: String by lazy { super.encodedUriForm }

    override fun toString(): String {
        return qualifiedForm
    }
}
