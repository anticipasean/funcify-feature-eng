package funcify.feature.datasource.reldb.filter

import funcify.feature.datasource.reldb.RelTableIdentifier


/**
 *
 * @author smccarron
 * @created 3/4/22
 */
interface RelDatabaseSourceContainerConfigurationFilter : (RelTableIdentifier) -> Boolean {

    companion object {
        @JvmStatic
        val INCLUDE_ALL: RelDatabaseSourceContainerConfigurationFilter = { true } as RelDatabaseSourceContainerConfigurationFilter
    }

    override fun invoke(relationalTableIdentifier: RelTableIdentifier): Boolean
}