package funcify.feature.datasource.db.schema

import funcify.feature.datasource.reldb.RelDatabaseSourceAttribute
import funcify.feature.datasource.reldb.RelDatabaseSourceContainerType
import funcify.feature.datasource.reldb.RelTableIdentifier
import funcify.feature.schema.SchematicPath
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.path.DefaultSchematicPath
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf


/**
 *
 * @author smccarron
 * @created 2/27/22
 */
data class JooqSourceContainerType(val jooqRelTable: JooqRelTable) : RelDatabaseSourceContainerType {

    override val relTableIdentifier: RelTableIdentifier by lazy { JooqRelTableIdentifier.fromJooqRelTable(jooqRelTable) }
    override val sourceAttributes: ImmutableSet<RelDatabaseSourceAttribute> = persistentSetOf()
    override val name: String = jooqRelTable.name
    override val canonicalPath: SchematicPath = DefaultSchematicPath()

    val sourceAttributesByName: ImmutableMap<String, RelDatabaseSourceAttribute> by lazy {
        sourceAttributes.fold(persistentMapOf(),
                              { acc, relDatabaseSourceAttribute ->
                                  acc.put(relDatabaseSourceAttribute.name,
                                          relDatabaseSourceAttribute)
                              })
    }

    override fun getSourceAttributeWithName(name: String): SourceAttribute? {
        return sourceAttributesByName[name]
    }

}
