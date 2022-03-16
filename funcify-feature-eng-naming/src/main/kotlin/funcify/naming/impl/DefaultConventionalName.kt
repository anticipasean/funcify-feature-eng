package funcify.naming.impl

import funcify.naming.ConventionalName
import funcify.naming.NameComponent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
data class DefaultConventionalName(override val namingConventionKey: Any,
                                   private val stringComponentNames: List<String>,
                                   override val delimiter: String) : ConventionalName {

    init {
        if (stringComponentNames.isEmpty()) {
            throw IllegalArgumentException("must have at least one name component")
        }
    }

    override val nameComponents: ImmutableList<NameComponent> by lazy {
        stringComponentNames.asSequence()
                .flatMap { s -> // since name components cannot contain the delimiter
                    // if the delimiter is non-empty, then split based on containment
                    if (delimiter.isNotEmpty() && s.contains(delimiter,
                                                             true)) {
                        s.splitToSequence(delimiter,
                                          ignoreCase = true)
                    } else {
                        sequenceOf(s)
                    }
                }
                .map { s -> DefaultNameComponent(s) }
                .fold(persistentListOf()) { acc: PersistentList<NameComponent>, nc: DefaultNameComponent ->
                    acc.add(nc)
                }
    }

    override val qualifiedForm: String by lazy { super.qualifiedForm }
    override val uriForm: String by lazy { super.uriForm }

    override fun toString(): String {
        return qualifiedForm
    }
}
