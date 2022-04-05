package funcify.feature.naming.impl

import funcify.feature.naming.ConventionalName
import funcify.feature.naming.NameSegment
import kotlinx.collections.immutable.ImmutableList


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
data class DefaultConventionalName(override val namingConventionKey: Any,
                                   override val nameSegments: ImmutableList<NameSegment>,
                                   override val delimiter: String = ConventionalName.EMPTY_STRING_DELIMITER) : ConventionalName {

    init {
        if (nameSegments.isEmpty()) {
            throw IllegalArgumentException("must have at least one name segment")
        }
    }

    override val qualifiedForm: String by lazy { super.qualifiedForm }
    override val encodedUriForm: String by lazy { super.encodedUriForm }

    override fun toString(): String {
        return qualifiedForm
    }
}
