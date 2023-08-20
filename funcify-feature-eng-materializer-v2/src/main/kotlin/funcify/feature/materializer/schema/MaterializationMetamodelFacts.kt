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

    val childCanonicalPathsByParentPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>

    val querySchemaElementsByCanonicalPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement>

    val fieldCoordinatesByCanonicalPath: ImmutableMap<GQLOperationPath, FieldCoordinates>

    val canonicalPathsByFieldCoordinates: ImmutableMap<FieldCoordinates, GQLOperationPath>

    val domainSpecifiedDataElementSourceByPath:
        ImmutableMap<GQLOperationPath, DomainSpecifiedDataElementSource>

    val featureSpecifiedFeatureCalculatorsByPath:
        ImmutableMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator>

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

        fun putDomainSpecifiedDataElementSourceForPath(
            path: GQLOperationPath,
            domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource
        ): Builder

        fun putAllDomainSpecifiedDataElementSources(
            pathDataElementSourcePairs:
                Iterable<Pair<GQLOperationPath, DomainSpecifiedDataElementSource>>
        ): Builder {
            return pathDataElementSourcePairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, dsdes: DomainSpecifiedDataElementSource) ->
                b.putDomainSpecifiedDataElementSourceForPath(p, dsdes)
            }
        }

        fun putAllDomainSpecifiedDataElementSources(
            pathDataElementSourcePairs: Map<GQLOperationPath, DomainSpecifiedDataElementSource>
        ): Builder {
            return pathDataElementSourcePairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, dsdes: DomainSpecifiedDataElementSource) ->
                b.putDomainSpecifiedDataElementSourceForPath(p, dsdes)
            }
        }

        fun putFeatureSpecifiedFeatureCalculatorForPath(
            path: GQLOperationPath,
            featureSpecifiedFeatureCalculator: FeatureSpecifiedFeatureCalculator
        ): Builder

        fun putAllFeatureSpecifiedFeatureCalculators(
            pathFeatureSpecifiedFeatureCalculatorPairs:
                Iterable<Pair<GQLOperationPath, FeatureSpecifiedFeatureCalculator>>
        ): Builder {
            return pathFeatureSpecifiedFeatureCalculatorPairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, fsfc: FeatureSpecifiedFeatureCalculator) ->
                b.putFeatureSpecifiedFeatureCalculatorForPath(p, fsfc)
            }
        }

        fun putAllFeatureSpecifiedFeatureCalculators(
            pathFeatureSpecifiedFeatureCalculatorPairs:
                Map<GQLOperationPath, FeatureSpecifiedFeatureCalculator>
        ): Builder {
            return pathFeatureSpecifiedFeatureCalculatorPairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, fsfc: FeatureSpecifiedFeatureCalculator) ->
                b.putFeatureSpecifiedFeatureCalculatorForPath(p, fsfc)
            }
        }

        fun build(): MaterializationMetamodelFacts
    }
}
