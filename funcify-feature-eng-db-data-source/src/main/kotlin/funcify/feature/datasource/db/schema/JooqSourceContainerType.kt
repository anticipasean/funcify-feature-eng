package funcify.feature.datasource.db.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.naming.impl.DefaultConventionalName
import funcify.feature.naming.impl.DefaultNameSegment
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2/27/22
 */
data class JooqSourceContainerType(val jooqRelTable: JooqRelTable) :
    RelDatabaseSourceContainerType {

    override val relTableIdentifier: RelTableIdentifier by lazy {
        JooqRelTableIdentifier.fromJooqRelTable(jooqRelTable)
    }
    override val sourceAttributes: ImmutableSet<RelDatabaseSourceAttribute> = persistentSetOf()
    override val name: ConventionalName by lazy {
        DefaultConventionalName(
            "JOOQ_TABLE_NAME",
            persistentListOf(DefaultNameSegment(jooqRelTable.name))
        )
    }
    override val sourcePath: SchematicPath =
        SchematicPath.getRootPath().transform {
            pathSegment(
                jooqRelTable.catalog?.name ?: "",
                jooqRelTable.schema?.name ?: "",
                jooqRelTable.name
            )
        }

    override val dataSourceLookupKey: DataSource.Key<RelDatabaseSourceIndex> by lazy {
        JooqDataSourceKey(
            jooqRelTable.catalog?.name
                ?: throw IllegalArgumentException(
                    """cannot create data_source.key for absent 
                    |catalog name for relational_database_index
                    |""".flatten()
                )
        )
    }

    val sourceAttributesByName: PersistentMap<String, RelDatabaseSourceAttribute> by lazy {
        sourceAttributes.fold(
            persistentMapOf(),
            { acc, relDatabaseSourceAttribute ->
                acc.put(relDatabaseSourceAttribute.name.qualifiedForm, relDatabaseSourceAttribute)
            }
        )
    }

    override fun getSourceAttributeWithName(name: String): RelDatabaseSourceAttribute? {
        return sourceAttributesByName[name]
    }
}
