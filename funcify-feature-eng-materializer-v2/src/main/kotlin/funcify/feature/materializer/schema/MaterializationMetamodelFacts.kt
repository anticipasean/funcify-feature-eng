package funcify.feature.materializer.schema

import arrow.core.foldLeft
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchemaElement
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-08-10
 */
internal interface MaterializationMetamodelFacts {

    val querySchemaElementsByPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement>

    val fieldCoordinatesByPath: ImmutableMap<GQLOperationPath, ImmutableSet<FieldCoordinates>>

    val pathsByFieldCoordinates: ImmutableMap<FieldCoordinates, ImmutableSet<GQLOperationPath>>

    fun update(transformer: Builder.() -> Builder): MaterializationMetamodelFacts

    interface Builder {

        fun putGraphQLSchemaElement(path: GQLOperationPath, element: GraphQLSchemaElement): Builder

        fun putAllGraphQLSchemaElements(
            pathElementPairs: Iterable<Pair<GQLOperationPath, GraphQLSchemaElement>>
        ): Builder {
            return pathElementPairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, e: GraphQLSchemaElement) ->
                b.putGraphQLSchemaElement(p, e)
            }
        }

        fun putAllGraphQLSchemaElements(
            pathElementPairs: Map<GQLOperationPath, GraphQLSchemaElement>
        ): Builder {
            return pathElementPairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, e: GraphQLSchemaElement) ->
                b.putGraphQLSchemaElement(p, e)
            }
        }

        fun putFieldCoordinates(path: GQLOperationPath, fieldCoordinates: FieldCoordinates): Builder

        fun putAllFieldCoordinates(
            pathFieldCoordinatesPairs: Iterable<Pair<GQLOperationPath, FieldCoordinates>>
        ): Builder {
            return pathFieldCoordinatesPairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, fc: FieldCoordinates) ->
                b.putFieldCoordinates(p, fc)
            }
        }

        fun putAllFieldCoordinates(
            pathFieldCoordinatesSetPairs: Map<GQLOperationPath, Set<FieldCoordinates>>
        ): Builder {
            return pathFieldCoordinatesSetPairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, fcSet: Set<FieldCoordinates>) ->
                fcSet.fold(b) { b1: Builder, fc: FieldCoordinates -> b1.putFieldCoordinates(p, fc) }
            }
        }

        fun putPath(fieldCoordinates: FieldCoordinates, path: GQLOperationPath): Builder

        fun putAllPaths(
            fieldCoordinatesPathPairs: Iterable<Pair<FieldCoordinates, GQLOperationPath>>
        ): Builder {
            return fieldCoordinatesPathPairs.fold(this) {
                b: Builder,
                (fc: FieldCoordinates, p: GQLOperationPath) ->
                b.putPath(fc, p)
            }
        }

        fun putAllPaths(
            fieldCoordinatesPathPairs: Map<FieldCoordinates, Set<GQLOperationPath>>
        ): Builder {
            return fieldCoordinatesPathPairs.foldLeft(this) {
                b: Builder,
                (fc: FieldCoordinates, ps: Set<GQLOperationPath>) ->
                ps.fold(b) { b1: Builder, p: GQLOperationPath -> b1.putPath(fc, p) }
            }
        }

        fun build(): MaterializationMetamodelFacts
    }
}
