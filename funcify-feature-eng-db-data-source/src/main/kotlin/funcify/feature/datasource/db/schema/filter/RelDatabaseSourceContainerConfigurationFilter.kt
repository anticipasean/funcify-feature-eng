package funcify.feature.datasource.db.schema.filter

import funcify.feature.datasource.db.schema.RelTableIdentifier

/**
 *
 * @author smccarron
 * @created 3/4/22
 */
fun interface RelDatabaseSourceContainerConfigurationFilter : (RelTableIdentifier) -> Boolean {

    companion object {
        @JvmStatic
        val INCLUDE_ALL: RelDatabaseSourceContainerConfigurationFilter =
            RelDatabaseSourceContainerConfigurationFilter {
                true
            }
    }

    override fun invoke(relationalTableIdentifier: RelTableIdentifier): Boolean
}
