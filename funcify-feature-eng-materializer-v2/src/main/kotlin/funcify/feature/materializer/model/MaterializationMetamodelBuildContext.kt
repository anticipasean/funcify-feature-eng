package funcify.feature.materializer.model

import arrow.core.foldLeft
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.directive.alias.AliasCoordinatesRegistry
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-08-10
 */
interface MaterializationMetamodelBuildContext {

    val featureEngineeringModel: FeatureEngineeringModel

    val materializationGraphQLSchema: GraphQLSchema

    val elementTypeCoordinates: ImmutableSet<FieldCoordinates>

    val elementTypePaths: ImmutableSet<GQLOperationPath>

    val dataElementElementTypePath: GQLOperationPath

    val featureElementTypePath: GQLOperationPath

    val transformerElementTypePath: GQLOperationPath

    val childPathsByParentPath: ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>

    val querySchemaElementsByPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement>

    val fieldCoordinatesByPath: ImmutableMap<GQLOperationPath, ImmutableSet<FieldCoordinates>>

    val pathsByFieldCoordinates: ImmutableMap<FieldCoordinates, ImmutableSet<GQLOperationPath>>

    val domainSpecifiedDataElementSourceByPath:
        ImmutableMap<GQLOperationPath, DomainSpecifiedDataElementSource>

    val domainSpecifiedDataElementSourcesByCoordinates:
        ImmutableMap<FieldCoordinates, DomainSpecifiedDataElementSource>

    val dataElementFieldCoordinatesByFieldName: ImmutableMap<String, ImmutableSet<FieldCoordinates>>

    val dataElementPathsByFieldName: ImmutableMap<String, ImmutableSet<GQLOperationPath>>

    val dataElementPathsByFieldArgumentName: ImmutableMap<String, ImmutableSet<GQLOperationPath>>

    val featureSpecifiedFeatureCalculatorsByPath:
        ImmutableMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator>

    val featureSpecifiedFeatureCalculatorsByCoordinates:
        ImmutableMap<FieldCoordinates, FeatureSpecifiedFeatureCalculator>

    val featurePathsByFieldName: ImmutableMap<String, GQLOperationPath>

    val transformerSpecifiedTransformerSourcesByPath:
        ImmutableMap<GQLOperationPath, TransformerSpecifiedTransformerSource>

    val transformerSpecifiedTransformerSourcesByCoordinates:
        ImmutableMap<FieldCoordinates, TransformerSpecifiedTransformerSource>

    val aliasCoordinatesRegistry: AliasCoordinatesRegistry

    fun update(transformer: Builder.() -> Builder): MaterializationMetamodelBuildContext

    interface Builder {

        fun featureEngineeringModel(featureEngineeringModel: FeatureEngineeringModel): Builder

        fun materializationGraphQLSchema(materializationGraphQLSchema: GraphQLSchema): Builder

        fun elementTypePaths(elementTypePaths: ImmutableSet<GQLOperationPath>): Builder

        fun dataElementElementTypePath(dataElementElementTypePath: GQLOperationPath): Builder

        fun featureElementTypePath(featureElementTypePath: GQLOperationPath): Builder

        fun transformerElementTypePath(transformerElementTypePath: GQLOperationPath): Builder

        fun addChildPathForParentPath(
            parentPath: GQLOperationPath,
            childPath: GQLOperationPath
        ): Builder

        fun putAllParentChildPaths(
            parentChildPairs: Iterable<Pair<GQLOperationPath, GQLOperationPath>>
        ): Builder {
            return parentChildPairs.fold(this) {
                b: Builder,
                (pp: GQLOperationPath, cp: GQLOperationPath) ->
                b.addChildPathForParentPath(pp, cp)
            }
        }

        fun putAllParentChildPaths(
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

        fun putAllGraphQLSchemaElementsForPaths(
            pathElementPairs: Iterable<Pair<GQLOperationPath, GraphQLSchemaElement>>
        ): Builder {
            return pathElementPairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, e: GraphQLSchemaElement) ->
                b.putGraphQLSchemaElementForPath(p, e)
            }
        }

        fun putAllGraphQLSchemaElementsForPaths(
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

        fun putAllPathsForFieldCoordinates(
            pathFieldCoordinatesPairs: Iterable<Pair<GQLOperationPath, FieldCoordinates>>
        ): Builder {
            return pathFieldCoordinatesPairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, fc: FieldCoordinates) ->
                b.putFieldCoordinatesForPath(p, fc)
            }
        }

        fun putAllPathsForFieldCoordinates(
            pathFieldCoordinatesSetPairs: Map<GQLOperationPath, Set<FieldCoordinates>>
        ): Builder {
            return pathFieldCoordinatesSetPairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, fcs: Set<FieldCoordinates>) ->
                fcs.fold(b) { b1: Builder, fc: FieldCoordinates ->
                    b1.putFieldCoordinatesForPath(p, fc)
                }
            }
        }

        fun putPathForFieldCoordinates(
            fieldCoordinates: FieldCoordinates,
            path: GQLOperationPath
        ): Builder

        fun putAllFieldCoordinatesForPaths(
            fieldCoordinatesPathPairs: Iterable<Pair<FieldCoordinates, GQLOperationPath>>
        ): Builder {
            return fieldCoordinatesPathPairs.fold(this) {
                b: Builder,
                (fc: FieldCoordinates, p: GQLOperationPath) ->
                b.putPathForFieldCoordinates(fc, p)
            }
        }

        fun putAllFieldCoordinatesForPaths(
            fieldCoordinatesPathPairs: Map<FieldCoordinates, Set<GQLOperationPath>>
        ): Builder {
            return fieldCoordinatesPathPairs.foldLeft(this) {
                b: Builder,
                (fc: FieldCoordinates, ps: Set<GQLOperationPath>) ->
                ps.fold(b) { b1: Builder, p: GQLOperationPath ->
                    b1.putPathForFieldCoordinates(fc, p)
                }
            }
        }

        fun putFieldCoordinatesForDataElementFieldName(
            name: String,
            fieldCoordinates: FieldCoordinates
        ): Builder

        fun putAllFieldCoordinatesForDataElementFieldNames(
            nameFieldCoordinatesPairs: Iterable<Pair<String, FieldCoordinates>>
        ): Builder {
            return nameFieldCoordinatesPairs.fold(this) {
                b: Builder,
                (name: String, fc: FieldCoordinates) ->
                b.putFieldCoordinatesForDataElementFieldName(name, fc)
            }
        }

        fun putAllFieldCoordinatesForDataElementFieldNames(
            nameFieldCoordinatesPairs: Map<String, Set<FieldCoordinates>>
        ): Builder {
            return nameFieldCoordinatesPairs.foldLeft(this) {
                b: Builder,
                (name: String, fcs: Set<FieldCoordinates>) ->
                fcs.fold(b) { b1: Builder, fc: FieldCoordinates ->
                    b1.putFieldCoordinatesForDataElementFieldName(name, fc)
                }
            }
        }

        fun putPathForDataElementFieldName(name: String, path: GQLOperationPath): Builder

        fun putAllPathsForDataElementFieldNames(
            namePathPairs: Iterable<Pair<String, GQLOperationPath>>
        ): Builder {
            return namePathPairs.fold(this) { b: Builder, (name: String, path: GQLOperationPath) ->
                b.putPathForDataElementFieldName(name, path)
            }
        }

        fun putAllPathsForDataElementFieldNames(
            namePathPairs: Map<String, Set<GQLOperationPath>>
        ): Builder {
            return namePathPairs.foldLeft(this) {
                b: Builder,
                (name: String, paths: Set<GQLOperationPath>) ->
                paths.fold(b) { b1: Builder, p: GQLOperationPath ->
                    b1.putPathForDataElementFieldName(name, p)
                }
            }
        }

        fun putPathForDataElementFieldArgumentName(name: String, path: GQLOperationPath): Builder

        fun putAllPathsForDataElementFieldArguments(
            namePathPairs: Iterable<Pair<String, GQLOperationPath>>
        ): Builder {
            return namePathPairs.fold(this) { b: Builder, (n: String, p: GQLOperationPath) ->
                b.putPathForDataElementFieldArgumentName(n, p)
            }
        }

        fun putAllPathsForDataElementFieldArguments(
            namePathPairs: Map<String, Set<GQLOperationPath>>
        ): Builder {
            return namePathPairs.foldLeft(this) { b: Builder, (n: String, ps: Set<GQLOperationPath>)
                ->
                ps.fold(b) { b1: Builder, p: GQLOperationPath ->
                    b1.putPathForDataElementFieldArgumentName(n, p)
                }
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

        fun putDomainSpecifiedDataElementSourceForCoordinates(
            fieldCoordinates: FieldCoordinates,
            domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource
        ): Builder

        fun putAllDomainSpecifiedDataElementSourceForCoordinates(
            coordinatesDomainSpecifiedDataElementSourcePairs:
                Iterable<Pair<FieldCoordinates, DomainSpecifiedDataElementSource>>
        ): Builder {
            return coordinatesDomainSpecifiedDataElementSourcePairs.fold(this) {
                b: Builder,
                (fc: FieldCoordinates, dsdes: DomainSpecifiedDataElementSource) ->
                b.putDomainSpecifiedDataElementSourceForCoordinates(fc, dsdes)
            }
        }

        fun putAllDomainSpecifiedDataElementSourceForCoordinates(
            coordinatesDomainSpecifiedDataElementSourcePairs:
                Map<FieldCoordinates, DomainSpecifiedDataElementSource>
        ): Builder {
            return coordinatesDomainSpecifiedDataElementSourcePairs.foldLeft(this) {
                b: Builder,
                (fc: FieldCoordinates, dsdes: DomainSpecifiedDataElementSource) ->
                b.putDomainSpecifiedDataElementSourceForCoordinates(fc, dsdes)
            }
        }

        fun putFeatureSpecifiedFeatureCalculatorForPath(
            path: GQLOperationPath,
            featureSpecifiedFeatureCalculator: FeatureSpecifiedFeatureCalculator
        ): Builder

        fun putAllPathFeatureSpecifiedFeatureCalculatorPairs(
            pathFeatureSpecifiedFeatureCalculatorPairs:
                Iterable<Pair<GQLOperationPath, FeatureSpecifiedFeatureCalculator>>
        ): Builder {
            return pathFeatureSpecifiedFeatureCalculatorPairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, fsfc: FeatureSpecifiedFeatureCalculator) ->
                b.putFeatureSpecifiedFeatureCalculatorForPath(p, fsfc)
            }
        }

        fun putAllPathFeatureSpecifiedFeatureCalculatorPairs(
            pathFeatureSpecifiedFeatureCalculatorPairs:
                Map<GQLOperationPath, FeatureSpecifiedFeatureCalculator>
        ): Builder {
            return pathFeatureSpecifiedFeatureCalculatorPairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, fsfc: FeatureSpecifiedFeatureCalculator) ->
                b.putFeatureSpecifiedFeatureCalculatorForPath(p, fsfc)
            }
        }

        fun putFeatureSpecifiedFeatureCalculatorForCoordinates(
            coordinates: FieldCoordinates,
            featureSpecifiedFeatureCalculator: FeatureSpecifiedFeatureCalculator
        ): Builder

        fun putAllCoordinatesFeatureSpecifiedFeatureCalculatorPairs(
            coordinatesFeatureSpecifiedFeatureCalculatorPairs:
                Iterable<Pair<FieldCoordinates, FeatureSpecifiedFeatureCalculator>>
        ): Builder {
            return coordinatesFeatureSpecifiedFeatureCalculatorPairs.fold(this) {
                b: Builder,
                (fc: FieldCoordinates, fsfc: FeatureSpecifiedFeatureCalculator) ->
                b.putFeatureSpecifiedFeatureCalculatorForCoordinates(fc, fsfc)
            }
        }

        fun putAllCoordinatesFeatureSpecifiedFeatureCalculatorPairs(
            coordinatesFeatureSpecifiedFeatureCalculatorPairs:
                Map<FieldCoordinates, FeatureSpecifiedFeatureCalculator>
        ): Builder {
            return coordinatesFeatureSpecifiedFeatureCalculatorPairs.foldLeft(this) {
                b: Builder,
                (fc: FieldCoordinates, fsfc: FeatureSpecifiedFeatureCalculator) ->
                b.putFeatureSpecifiedFeatureCalculatorForCoordinates(fc, fsfc)
            }
        }

        fun putFeatureNameForPath(name: String, gqlOperationPath: GQLOperationPath): Builder

        fun putAllFeatureNamesForPath(
            namePathPairs: Iterable<Pair<String, GQLOperationPath>>
        ): Builder {
            return namePathPairs.fold(this) { b: Builder, (name: String, p: GQLOperationPath) ->
                b.putFeatureNameForPath(name, p)
            }
        }

        fun putAllFeatureNamesForPath(namePathPairs: Map<String, GQLOperationPath>): Builder {
            return namePathPairs.foldLeft(this) { b: Builder, (name: String, p: GQLOperationPath) ->
                b.putFeatureNameForPath(name, p)
            }
        }

        fun putTransformerSpecifiedTransformerSourceForPath(
            path: GQLOperationPath,
            transformerSpecifiedTransformerSource: TransformerSpecifiedTransformerSource
        ): Builder

        fun putAllPathTransformerSourcePairs(
            pathTransformerSourcePairs:
                Iterable<Pair<GQLOperationPath, TransformerSpecifiedTransformerSource>>
        ): Builder {
            return pathTransformerSourcePairs.fold(this) {
                b: Builder,
                (p: GQLOperationPath, ts: TransformerSpecifiedTransformerSource) ->
                b.putTransformerSpecifiedTransformerSourceForPath(p, ts)
            }
        }

        fun putAllPathTransformerSourcePairs(
            pathTransformerSpecifiedTransformerSourcePairs:
                Map<GQLOperationPath, TransformerSpecifiedTransformerSource>
        ): Builder {
            return pathTransformerSpecifiedTransformerSourcePairs.foldLeft(this) {
                b: Builder,
                (p: GQLOperationPath, ts: TransformerSpecifiedTransformerSource) ->
                b.putTransformerSpecifiedTransformerSourceForPath(p, ts)
            }
        }

        fun putTransformerSpecifiedTransformerSourceForCoordinates(
            fieldCoordinates: FieldCoordinates,
            transformerSpecifiedTransformerSource: TransformerSpecifiedTransformerSource
        ): Builder

        fun putAllCoordinatesTransformerSpecifiedTransformerSourcePairs(
            coordinatesTransformerSourcePairs:
                Iterable<Pair<FieldCoordinates, TransformerSpecifiedTransformerSource>>
        ): Builder {
            return coordinatesTransformerSourcePairs.fold(this) {
                b: Builder,
                (fc: FieldCoordinates, ts: TransformerSpecifiedTransformerSource) ->
                b.putTransformerSpecifiedTransformerSourceForCoordinates(fc, ts)
            }
        }

        fun putAllCoordinatesTransformerSpecifiedTransformerSourcePairs(
            coordinatesTransformerSourcePairs:
                Map<FieldCoordinates, TransformerSpecifiedTransformerSource>
        ): Builder {
            return coordinatesTransformerSourcePairs.foldLeft(this) {
                b: Builder,
                (fc: FieldCoordinates, ts: TransformerSpecifiedTransformerSource) ->
                b.putTransformerSpecifiedTransformerSourceForCoordinates(fc, ts)
            }
        }

        fun aliasCoordinatesRegistry(aliasCoordinatesRegistry: AliasCoordinatesRegistry): Builder

        fun build(): MaterializationMetamodelBuildContext
    }
}
