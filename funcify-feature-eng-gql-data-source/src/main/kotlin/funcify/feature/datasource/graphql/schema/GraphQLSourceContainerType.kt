package funcify.feature.datasource.graphql.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import graphql.schema.GraphQLType
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2/7/22
 */
data class GraphQLSourceContainerType(
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val type: GraphQLType,
    override val sourceAttributes: PersistentSet<GraphQLSourceAttribute> = persistentSetOf()
) : GraphQLSourceIndex, SourceContainerType<GraphQLSourceAttribute> {

    val sourceAttributesByName: PersistentMap<String, GraphQLSourceAttribute> by lazy {
        sourceAttributes
            .parallelStream()
            .map { gqlsa -> gqlsa.name.qualifiedForm to gqlsa }
            .reducePairsToPersistentMap()
    }

    override fun getSourceAttributeWithName(name: String): GraphQLSourceAttribute? {
        return sourceAttributesByName[name]
    }
}
