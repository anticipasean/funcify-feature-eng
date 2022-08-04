package funcify.feature.scalar.registry

import graphql.language.Description
import graphql.language.ScalarTypeDefinition
import graphql.language.SourceLocation
import graphql.schema.GraphQLScalarType

/**
 * Default implementation of the scalar type registry intended to remain internal to this module
 * @author smccarron
 * @created 2022-08-03
 */
internal data class DefaultScalarTypeRegistry(
    private val graphQLScalarTypesByName: Map<String, GraphQLScalarType> = mapOf()
) : ScalarTypeRegistry {

    private val graphQLScalarTypesList: List<GraphQLScalarType> by lazy {
        graphQLScalarTypesByName.values.toList()
    }

    private val scalarTypeDefinitionsByName: Map<String, ScalarTypeDefinition> by lazy {
        graphQLScalarTypesByName.values
            .asSequence()
            .mapNotNull { graphQLScalarType: GraphQLScalarType ->
                if (graphQLScalarType.definition == null) {
                    // TODO: Consider copying any directives applied to graphql_scalar_type over to
                    // type_definition
                    ScalarTypeDefinition.newScalarTypeDefinition()
                        .name(graphQLScalarType.name)
                        .description(
                            Description(
                                graphQLScalarType.description ?: "",
                                SourceLocation.EMPTY,
                                graphQLScalarType.description?.contains(System.lineSeparator())
                                    ?: false
                            )
                        )
                        .build()
                } else {
                    graphQLScalarType.definition
                }
            }
            .fold(sortedMapOf<String, ScalarTypeDefinition>(Comparator.naturalOrder())) { mm, def ->
                mm.apply { put(def.name, def) }
            }
    }

    private val scalarTypeDefinitionsList: List<ScalarTypeDefinition> by lazy {
        scalarTypeDefinitionsByName.values.toList()
    }

    override fun registerScalarType(graphQLScalarType: GraphQLScalarType): ScalarTypeRegistry {
        return DefaultScalarTypeRegistry(
            graphQLScalarTypesByName =
                graphQLScalarTypesByName.run { plus(graphQLScalarType.name to graphQLScalarType) }
        )
    }

    override fun getAllScalarDefinitions(): List<ScalarTypeDefinition> {
        return scalarTypeDefinitionsList
    }

    override fun getAllGraphQLScalarTypes(): List<GraphQLScalarType> {
        return graphQLScalarTypesList
    }

    override fun getScalarTypeDefinitionWithName(name: String): ScalarTypeDefinition? {
        return scalarTypeDefinitionsByName[name]
    }

    override fun getGraphQLScalarTypeWithName(name: String): GraphQLScalarType? {
        return graphQLScalarTypesByName[name]
    }
}
