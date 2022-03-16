package funcify.naming.impl

import funcify.naming.NameComponent


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
data class DefaultNameComponent(override val value: String) : NameComponent {

    override fun toString(): String {
        return value
    }
}
