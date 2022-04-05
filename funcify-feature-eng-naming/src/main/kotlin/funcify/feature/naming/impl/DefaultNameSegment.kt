package funcify.feature.naming.impl

import funcify.feature.naming.NameSegment


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
data class DefaultNameSegment(override val value: String) : NameSegment {

    override fun toString(): String {
        return value
    }
}
