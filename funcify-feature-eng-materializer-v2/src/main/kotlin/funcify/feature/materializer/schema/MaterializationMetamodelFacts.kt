package funcify.feature.materializer.schema

import arrow.core.foldLeft
import funcify.feature.schema.dataelement.DataElementSource
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

    val childCanonicalPathsByParentPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>

    val querySchemaElementsByCanonicalPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement>

    val fieldCoordinatesByCanonicalPath: ImmutableMap<GQLOperationPath, FieldCoordinates>

    val canonicalPathsByFieldCoordinates: ImmutableMap<FieldCoordinates, GQLOperationPath>

    val dataElementSourceByDomainPath: ImmutableMap<GQLOperationPath, DataElementSource>

    fun update(transformer: Builder.() -> Builder): MaterializationMetamodelFacts

    interface Builder {

        fun addChildPathForParentPath(
            parentPath: GQLOperationPath,
            childPath: GQLOperationPath
        ): Builder

        fun addAllParentChildPaths(
            parentChildPairs: Iterable<Pair<GQLOperationPath, GQLOperationPath>>
        ): Builder {
            return parentChildPairs.fold(this) {
                b: Builder,
                (pp: GQLOperationPath, cp: GQLOperationPath) ->
                b.addChildPathForParentPath(pp, cp)
            }
        }

        fun addAllParentChildPaths(
            parentChildPairs: Map<GQLOperationPath, Set<GQLOperationPath>>
        ): Builder {
            return parentChildPairs.foldLeft(this) {
                b: Builder,
                (pp: GQLOperationPath, cps: Set<GQLOperationPath>) ->
                cps.fold(b) { b1: Builder, cp: GQLOperationPath ->
                    b1.addChildPathForParentPath(pp, cp)
                }
            }
        }

        fun putGraphQLSchemaElementForPath(
            path: GQLOperationPath,
            element: GraphQLSchemaElement
        ): Builder

        fun putAllGraphQLSchemaElements(
            pathElementPairs: Iterable<Pair<GQLOperationPath, GraphQLSchemaElement>>
        ): Builder {
            return pathElementPairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, e: GraphQLSchemaElement) ->
                b.putGraphQLSchemaElementForPath(p, e)
            }
        }

        fun putAllGraphQLSchemaElements(
            pathElementPairs: Map<GQLOperationPath, GraphQLSchemaElement>
        ): Builder {
            return pathElementPairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, e: GraphQLSchemaElement) ->
                b.putGraphQLSchemaElementForPath(p, e)
            }
        }

        fun putFieldCoordinatesForPath(
            path: GQLOperationPath,
            fieldCoordinates: FieldCoordinates
        ): Builder

        fun putAllFieldCoordinates(
            pathFieldCoordinatesPairs: Iterable<Pair<GQLOperationPath, FieldCoordinates>>
        ): Builder {
            return pathFieldCoordinatesPairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, fc: FieldCoordinates) ->
                b.putFieldCoordinatesForPath(p, fc)
            }
        }

        fun putAllFieldCoordinates(
            pathFieldCoordinatesSetPairs: Map<GQLOperationPath, FieldCoordinates>
        ): Builder {
            return pathFieldCoordinatesSetPairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, fc: FieldCoordinates) ->
                b.putFieldCoordinatesForPath(p, fc)
            }
        }

        fun putPathForFieldCoordinates(
            fieldCoordinates: FieldCoordinates,
            path: GQLOperationPath
        ): Builder

        fun putAllFieldCoordinatesPaths(
            fieldCoordinatesPathPairs: Iterable<Pair<FieldCoordinates, GQLOperationPath>>
        ): Builder {
            return fieldCoordinatesPathPairs.fold(this) {
                b: Builder,
                (fc: FieldCoordinates, p: GQLOperationPath) ->
                b.putPathForFieldCoordinates(fc, p)
            }
        }

        fun putAllFieldCoordinatesPaths(
            fieldCoordinatesPathPairs: Map<FieldCoordinates, GQLOperationPath>
        ): Builder {
            return fieldCoordinatesPathPairs.foldLeft(this) {
                b: Builder,
                (fc: FieldCoordinates, p: GQLOperationPath) ->
                b.putPathForFieldCoordinates(fc, p)
            }
        }

        fun putDataElementSourceForDomainPath(
            path: GQLOperationPath,
            dataElementSource: DataElementSource
        ): Builder

        fun putAllDataElementSources(
            pathDataElementSourcePairs: Iterable<Pair<GQLOperationPath, DataElementSource>>
        ): Builder {
            return pathDataElementSourcePairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, des: DataElementSource) ->
                b.putDataElementSourceForDomainPath(p, des)
            }
        }

        fun putAllDataElementSources(
            pathDataElementSourcePairs: Map<GQLOperationPath, DataElementSource>
        ): Builder {
            return pathDataElementSourcePairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, des: DataElementSource) ->
                b.putDataElementSourceForDomainPath(p, des)
            }
        }

        fun build(): MaterializationMetamodelFacts
    }
}
