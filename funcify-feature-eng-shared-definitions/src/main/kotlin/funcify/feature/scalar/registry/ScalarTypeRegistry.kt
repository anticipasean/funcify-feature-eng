package funcify.feature.scalar.registry

import funcify.feature.scalar.decimal.Decimal16
import funcify.feature.scalar.decimal.Decimal3
import funcify.feature.scalar.decimal.Decimal7
import graphql.Scalars
import graphql.language.ScalarTypeDefinition
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLScalarType
import java.util.*

/**
 * Registry of the [GraphQLScalarType]s to be used throughout a given service or GraphQL setup
 *
 * @author smccarron
 * @created 2022-08-03
 */
interface ScalarTypeRegistry {

    companion object {

        fun emptyRegistry(): ScalarTypeRegistry {
            return DefaultScalarTypeRegistry()
        }

        fun customRegistry(graphQLScalarTypes: Iterable<GraphQLScalarType>): ScalarTypeRegistry {
            return DefaultScalarTypeRegistry(
                graphQLScalarTypes.fold(sortedMapOf(Comparator.naturalOrder())) {
                    mm: SortedMap<String, GraphQLScalarType>,
                    gst: GraphQLScalarType ->
                    mm.apply { put(gst.name, gst) }
                }
            )
        }

        /**
         * Instance containing the scalars from the following sets:
         * - the standard [Scalars] from the graphql-java framework
         * - the custom scalar implementation from this module e.g. [Decimal7]
         * - the extended scalars from the [ExtendedScalars] dependency
         */
        fun materializationRegistry(): ScalarTypeRegistry {
            return DefaultScalarTypeRegistry(
                sequenceOf(
                        Scalars.GraphQLID,
                        Scalars.GraphQLBoolean,
                        Scalars.GraphQLInt,
                        Scalars.GraphQLFloat,
                        Scalars.GraphQLString,
                        Decimal3.graphQLScalarType,
                        Decimal7.graphQLScalarType,
                        Decimal16.graphQLScalarType,
                        ExtendedScalars.GraphQLBigDecimal,
                        ExtendedScalars.GraphQLBigInteger,
                        ExtendedScalars.GraphQLByte,
                        ExtendedScalars.GraphQLChar,
                        ExtendedScalars.GraphQLLong,
                        ExtendedScalars.GraphQLShort,
                        ExtendedScalars.Date,
                        ExtendedScalars.DateTime,
                        ExtendedScalars.Time,
                        ExtendedScalars.Json,
                        ExtendedScalars.Locale,
                        ExtendedScalars.PositiveFloat,
                        ExtendedScalars.PositiveInt,
                        ExtendedScalars.NegativeFloat,
                        ExtendedScalars.NegativeInt,
                        ExtendedScalars.Url
                    )
                    .fold(sortedMapOf<String, GraphQLScalarType>(Comparator.naturalOrder())) {
                        mm: SortedMap<String, GraphQLScalarType>,
                        gst: GraphQLScalarType ->
                        mm.apply { put(gst.name, gst) }
                    }
            )
        }
    }

    /**
     * Immutable registry so registering a new scalar returns a new instance of the registry with
     * the scalar type added
     */
    fun registerScalarType(graphQLScalarType: GraphQLScalarType): ScalarTypeRegistry

    fun getAllScalarDefinitions(): List<ScalarTypeDefinition>

    fun getAllGraphQLScalarTypes(): List<GraphQLScalarType>

    fun getScalarTypeDefinitionWithName(name: String): ScalarTypeDefinition?

    fun getGraphQLScalarTypeWithName(name: String): GraphQLScalarType?

    fun getBasicGraphQLScalarTypesByName(): Map<String, GraphQLScalarType>

    fun getExtendedGraphQLScalarTypesByName(): Map<String, GraphQLScalarType>

    fun getBasicScalarTypeDefinitionsByName(): Map<String, ScalarTypeDefinition>

    fun getExtendedScalarTypeDefinitionsByName(): Map<String, ScalarTypeDefinition>
}
