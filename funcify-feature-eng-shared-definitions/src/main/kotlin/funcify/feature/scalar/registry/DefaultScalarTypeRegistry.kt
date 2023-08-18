package funcify.feature.scalar.registry

import graphql.Scalars
import graphql.language.Description
import graphql.language.ScalarTypeDefinition
import graphql.language.SourceLocation
import graphql.schema.GraphQLScalarType
import java.util.*

/**
 * Default implementation of the scalar type registry intended to remain internal to this module
 *
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
            .fold(sortedMapOf<String, ScalarTypeDefinition>(Comparator.naturalOrder())) {
                mm: SortedMap<String, ScalarTypeDefinition>,
                def: ScalarTypeDefinition ->
                mm.apply { put(def.name, def) }
            }
    }

    private val scalarTypeDefinitionsList: List<ScalarTypeDefinition> by lazy {
        scalarTypeDefinitionsByName.values.toList()
    }

    private val basicScalarTypes: Map<String, GraphQLScalarType> by lazy {
        sequenceOf(
                Scalars.GraphQLID,
                Scalars.GraphQLString,
                Scalars.GraphQLBoolean,
                Scalars.GraphQLFloat,
                Scalars.GraphQLInt
            )
            .fold(sortedMapOf<String, GraphQLScalarType>(Comparator.naturalOrder())) {
                mm: SortedMap<String, GraphQLScalarType>,
                gst: GraphQLScalarType ->
                mm.apply { put(gst.name, gst) }
            }
    }

    private val basicScalarTypeDefinitions: Map<String, ScalarTypeDefinition> by lazy {
        sequenceOf(
                Scalars.GraphQLID,
                Scalars.GraphQLString,
                Scalars.GraphQLBoolean,
                Scalars.GraphQLFloat,
                Scalars.GraphQLInt
            )
            .flatMap { gst: GraphQLScalarType ->
                when (val std: ScalarTypeDefinition? = scalarTypeDefinitionsByName[gst.name]) {
                    null -> {
                        emptySequence()
                    }
                    else -> {
                        sequenceOf(std.name to std)
                    }
                }
            }
            .fold(sortedMapOf<String, ScalarTypeDefinition>(Comparator.naturalOrder())) {
                mm: SortedMap<String, ScalarTypeDefinition>,
                (name: String, std: ScalarTypeDefinition) ->
                mm.apply { put(name, std) }
            }
    }

    private val extendedScalarTypes: Map<String, GraphQLScalarType> by lazy {
        graphQLScalarTypesByName
            .asSequence()
            .filter { (name: String, _: GraphQLScalarType) -> name !in basicScalarTypes }
            .fold(sortedMapOf<String, GraphQLScalarType>(Comparator.naturalOrder())) {
                mm: SortedMap<String, GraphQLScalarType>,
                (name: String, gst: GraphQLScalarType) ->
                mm.apply { put(name, gst) }
            }
    }

    private val extendedScalarTypeDefinitions: Map<String, ScalarTypeDefinition> by lazy {
        scalarTypeDefinitionsByName
            .asSequence()
            .filter { (name: String, _: ScalarTypeDefinition) -> name !in basicScalarTypes }
            .fold(sortedMapOf<String, ScalarTypeDefinition>(Comparator.naturalOrder())) {
                mm: SortedMap<String, ScalarTypeDefinition>,
                (name: String, std: ScalarTypeDefinition) ->
                mm.apply { put(name, std) }
            }
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

    override fun getBasicGraphQLScalarTypesByName(): Map<String, GraphQLScalarType> {
        return basicScalarTypes
    }

    override fun getExtendedGraphQLScalarTypesByName(): Map<String, GraphQLScalarType> {
        return extendedScalarTypes
    }

    override fun getBasicScalarTypeDefinitionsByName(): Map<String, ScalarTypeDefinition> {
        return basicScalarTypeDefinitions
    }

    override fun getExtendedScalarTypeDefinitionsByName(): Map<String, ScalarTypeDefinition> {
        return extendedScalarTypeDefinitions
    }
}
