package funcify.feature.materializer.model

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.orElse
import arrow.core.toOption
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLNamedSchemaElement
import graphql.schema.GraphQLSchemaElement
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-21
 */
internal class DefaultMaterializationMetamodelBuildStrategy :
    MaterializationMetamodelBuildStrategy {

    companion object {
        private const val MAIN_METHOD_TAG: String = "build_materialization_metamodel"
        private val logger: Logger = loggerFor<DefaultMaterializationMetamodelBuildStrategy>()
    }

    override fun buildMaterializationMetamodel(
        context: MaterializationMetamodelBuildContext
    ): Mono<out MaterializationMetamodel> {
        logger.debug("{}: [ ]", MAIN_METHOD_TAG)
        return Mono.just(context)
            .flatMap(calculatePathsAndFieldCoordinates())
            .flatMap(calculateDomainSpecifiedDataElementSources())
            .flatMap(calculateFeatureSpecifiedFeatureCalculators())
            .doOnNext { mmf: MaterializationMetamodelBuildContext ->
                logger.debug(
                    "paths: {}",
                    mmf.querySchemaElementsByCanonicalPath
                        .asSequence()
                        .sortedBy(Map.Entry<GQLOperationPath, GraphQLSchemaElement>::key)
                        .joinToString(",\n") { (p: GQLOperationPath, e: GraphQLSchemaElement) ->
                            val elementString =
                                e.toOption()
                                    .filterIsInstance<GraphQLNamedSchemaElement>()
                                    .map(GraphQLNamedSchemaElement::getName)
                                    .zip(e.run { this::class }.simpleName.toOption()) { n, t ->
                                        "$n:$t"
                                    }
                                    .orElse { e.run { this::class }.qualifiedName.toOption() }
                                    .getOrElse { "<NA>" }
                            "${p.toDecodedURIString()}: $elementString"
                        }
                )
                logger.debug(
                    "coordinates: {}",
                    mmf.fieldCoordinatesByCanonicalPath
                        .asSequence()
                        .sortedBy(Map.Entry<GQLOperationPath, FieldCoordinates>::key)
                        .joinToString(",\n") { (p: GQLOperationPath, fc: FieldCoordinates) ->
                            "${p.toDecodedURIString()}: $fc"
                        }
                )
                logger.debug(
                    "paths: {}",
                    mmf.canonicalPathsByFieldCoordinates
                        .asSequence()
                        .sortedWith(
                            Comparator.comparing(
                                Map.Entry<FieldCoordinates, GQLOperationPath>::key,
                                Comparator.comparing(FieldCoordinates::getTypeName)
                                    .thenComparing(FieldCoordinates::getFieldName)
                            )
                        )
                        .joinToString(",\n") { (fc: FieldCoordinates, p: GQLOperationPath) ->
                            "${fc}: ${p.toDecodedURIString()}"
                        }
                )
                logger.debug(
                    "feature_specified_feature_calculators: {}",
                    mmf.featureSpecifiedFeatureCalculatorsByPath
                        .asSequence()
                        .sortedBy(
                            Map.Entry<GQLOperationPath, FeatureSpecifiedFeatureCalculator>::key
                        )
                        .joinToString(",\n") {
                            (p: GQLOperationPath, fsfc: FeatureSpecifiedFeatureCalculator) ->
                            "${p}: ${fsfc}"
                        }
                )
            }
            .map { mmbc: MaterializationMetamodelBuildContext ->
                DefaultMaterializationMetamodel(
                    featureEngineeringModel = mmbc.featureEngineeringModel,
                    materializationGraphQLSchema = mmbc.materializationGraphQLSchema,
                    childCanonicalPathsByParentPath = mmbc.childCanonicalPathsByParentPath,
                    querySchemaElementsByCanonicalPath = mmbc.querySchemaElementsByCanonicalPath,
                    fieldCoordinatesByCanonicalPath = mmbc.fieldCoordinatesByCanonicalPath,
                    canonicalPathsByFieldCoordinates = mmbc.canonicalPathsByFieldCoordinates,
                    domainSpecifiedDataElementSourceByPath =
                        mmbc.domainSpecifiedDataElementSourceByPath,
                    featureSpecifiedFeatureCalculatorsByPath =
                        mmbc.featureSpecifiedFeatureCalculatorsByPath,
                    featurePathsByName = mmbc.featurePathsByName,
                )
            }
    }

    private fun calculatePathsAndFieldCoordinates():
        (MaterializationMetamodelBuildContext) -> Mono<MaterializationMetamodelBuildContext> {
        return { mmbc: MaterializationMetamodelBuildContext ->
            Mono.fromCallable {
                PathCoordinatesGatherer.invoke(mmbc, mmbc.materializationGraphQLSchema)
            }
        }
    }

    private fun calculateDomainSpecifiedDataElementSources():
        (MaterializationMetamodelBuildContext) -> Mono<MaterializationMetamodelBuildContext> {
        return { mmbc: MaterializationMetamodelBuildContext ->
            Mono.fromCallable {
                mmbc.update {
                    DomainSpecifiedDataElementSourceCreator.invoke(
                            mmbc.featureEngineeringModel,
                            mmbc.materializationGraphQLSchema
                        )
                        .fold(this) {
                            b: MaterializationMetamodelBuildContext.Builder,
                            dsdes: DomainSpecifiedDataElementSource ->
                            b.putDomainSpecifiedDataElementSourceForPath(dsdes.domainPath, dsdes)
                        }
                }
            }
        }
    }

    private fun calculateFeatureSpecifiedFeatureCalculators():
        (MaterializationMetamodelBuildContext) -> Mono<MaterializationMetamodelBuildContext> {
        return { mmbc: MaterializationMetamodelBuildContext ->
            Mono.fromCallable {
                mmbc.update {
                    FeatureSpecifiedFeatureCalculatorCreator.invoke(
                            mmbc.featureEngineeringModel,
                            mmbc.materializationGraphQLSchema
                        )
                        .fold(this) {
                            b: MaterializationMetamodelBuildContext.Builder,
                            fsfc: FeatureSpecifiedFeatureCalculator ->
                            b.putFeatureSpecifiedFeatureCalculatorForPath(fsfc.featurePath, fsfc)
                                .putFeatureNameForPath(fsfc.featureName, fsfc.featurePath)
                        }
                }
            }
        }
    }
}
