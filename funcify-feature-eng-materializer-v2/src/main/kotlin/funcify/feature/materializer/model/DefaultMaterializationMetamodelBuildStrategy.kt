package funcify.feature.materializer.model

import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.orElse
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.directive.alias.AliasCoordinatesRegistryCreator
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLNamedSchemaElement
import graphql.schema.GraphQLSchemaElement
import kotlinx.collections.immutable.ImmutableSet
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
            .flatMap(calculateAttributeCoordinatesRegistry())
            .doOnNext { mmf: MaterializationMetamodelBuildContext ->
                logger.debug(
                    "query_schema_elements_by_path: {}",
                    mmf.querySchemaElementsByPath
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
                    mmf.fieldCoordinatesByPath
                        .asSequence()
                        .sortedBy(Map.Entry<GQLOperationPath, ImmutableSet<FieldCoordinates>>::key)
                        .joinToString(",\n") {
                            (p: GQLOperationPath, fcs: ImmutableSet<FieldCoordinates>) ->
                            "${p.toDecodedURIString()}: ${fcs.asSequence().joinToString(", ")}"
                        }
                )
                logger.debug(
                    "paths_by_field_coordinates: {}",
                    mmf.pathsByFieldCoordinates
                        .asSequence()
                        .sortedWith(
                            Comparator.comparing(
                                Map.Entry<FieldCoordinates, ImmutableSet<GQLOperationPath>>::key,
                                Comparator.comparing(FieldCoordinates::getTypeName)
                                    .thenComparing(FieldCoordinates::getFieldName)
                            )
                        )
                        .joinToString(",\n") {
                            (fc: FieldCoordinates, ps: ImmutableSet<GQLOperationPath>) ->
                            "${fc}: ${ps.asSequence().map(GQLOperationPath::toDecodedURIString).joinToString(", ")}"
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
                logger.debug(
                    "domain_specified_data_element_sources_by_path: {}",
                    mmf.domainSpecifiedDataElementSourceByPath.asSequence().joinToString("\n") {
                        (p, d) ->
                        "$p: $d"
                    }
                )
                logger.debug(
                    "data_element_field_coordinates_by_name: {}",
                    mmf.dataElementFieldCoordinatesByFieldName.asSequence().joinToString("\n") {
                        (fn, fcs) ->
                        "${fn}: ${fcs.asSequence().joinToString(", ")}"
                    }
                )
                logger.debug(
                    "data_element_paths_by_field_name: {}",
                    mmf.dataElementPathsByFieldName.asSequence().joinToString("\n") { (fn, ps) ->
                        "${fn}: ${ps.asSequence().joinToString(", ")}"
                    }
                )
                logger.debug(
                    "data_element_paths_by_field_argument_name: {}",
                    mmf.dataElementPathsByFieldArgumentName.asSequence().joinToString("\n") {
                        (fn, ps) ->
                        "${fn}: ${ps.asSequence().joinToString(", ")}"
                    }
                )
            }
            .map { mmbc: MaterializationMetamodelBuildContext ->
                DefaultMaterializationMetamodel(
                    featureEngineeringModel = mmbc.featureEngineeringModel,
                    materializationGraphQLSchema = mmbc.materializationGraphQLSchema,
                    childPathsByParentPath = mmbc.childPathsByParentPath,
                    querySchemaElementsByPath = mmbc.querySchemaElementsByPath,
                    fieldCoordinatesByPath = mmbc.fieldCoordinatesByPath,
                    pathsByFieldCoordinates = mmbc.pathsByFieldCoordinates,
                    domainSpecifiedDataElementSourceByPath =
                        mmbc.domainSpecifiedDataElementSourceByPath,
                    domainSpecifiedDataElementSourceByCoordinates =
                        mmbc.domainSpecifiedDataElementSourcesByCoordinates,
                    dataElementFieldCoordinatesByFieldName =
                        mmbc.dataElementFieldCoordinatesByFieldName,
                    dataElementPathsByFieldName = mmbc.dataElementPathsByFieldName,
                    dataElementPathByFieldArgumentName = mmbc.dataElementPathsByFieldArgumentName,
                    featureSpecifiedFeatureCalculatorsByPath =
                        mmbc.featureSpecifiedFeatureCalculatorsByPath,
                    featurePathsByName = mmbc.featurePathsByFieldName,
                    aliasCoordinatesRegistry = mmbc.aliasCoordinatesRegistry
                )
            }
    }

    private fun calculatePathsAndFieldCoordinates():
        (MaterializationMetamodelBuildContext) -> Mono<MaterializationMetamodelBuildContext> {
        return { mmbc: MaterializationMetamodelBuildContext ->
            Mono.fromCallable { PathCoordinatesGatherer.invoke(mmbc) }
                .onErrorMap { t: Throwable ->
                    when (t) {
                        is ServiceError -> {
                            t
                        }
                        else -> {
                            ServiceError.builder()
                                .message(
                                    "calculate_paths_and_field_coordinates: [ status: failed ]"
                                )
                                .cause(t)
                                .build()
                        }
                    }
                }
                .doOnError { t: Throwable -> logger.error("{}", t.message) }
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
                                b.putDomainSpecifiedDataElementSourceForPath(
                                        dsdes.domainPath,
                                        dsdes
                                    )
                                    .putDomainSpecifiedDataElementSourceForCoordinates(
                                        dsdes.domainFieldCoordinates,
                                        dsdes
                                    )
                            }
                    }
                }
                .map { m: MaterializationMetamodelBuildContext ->
                    m.update {
                        m.fieldCoordinatesByPath
                            .asSequence()
                            .filter { (p: GQLOperationPath, _: ImmutableSet<FieldCoordinates>) ->
                                p.selection.size > 1 &&
                                    p.selection
                                        .firstOrNone()
                                        .filterIsInstance<FieldSegment>()
                                        .map(FieldSegment::fieldName)
                                        .filter { fn: String ->
                                            m.featureEngineeringModel.dataElementFieldCoordinates
                                                .fieldName == fn
                                        }
                                        .isDefined()
                            }
                            .fold(this) {
                                b: MaterializationMetamodelBuildContext.Builder,
                                (p: GQLOperationPath, fcs: ImmutableSet<FieldCoordinates>) ->
                                fcs.fold(b) {
                                    b1: MaterializationMetamodelBuildContext.Builder,
                                    fc: FieldCoordinates ->
                                    b1.putFieldCoordinatesForDataElementFieldName(fc.fieldName, fc)
                                        .putPathForDataElementFieldName(fc.fieldName, p)
                                }
                            }
                    }
                }
                .map { m: MaterializationMetamodelBuildContext ->
                    m.update {
                        m.domainSpecifiedDataElementSourceByPath.values
                            .asSequence()
                            .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                                dsdes.argumentsByPath.asSequence().map {
                                    (p: GQLOperationPath, a: GraphQLArgument) ->
                                    a.name to p
                                }
                            }
                            .fold(this) {
                                b: MaterializationMetamodelBuildContext.Builder,
                                (n: String, p: GQLOperationPath) ->
                                b.putPathForDataElementFieldArgumentName(n, p)
                            }
                    }
                }
                .onErrorMap { t: Throwable ->
                    when (t) {
                        is ServiceError -> {
                            t
                        }
                        else -> {
                            ServiceError.builder()
                                .message(
                                    "calculate_domain_specified_data_element_sources: [ status: failed ]"
                                )
                                .cause(t)
                                .build()
                        }
                    }
                }
                .doOnError { t: Throwable -> logger.error("{}", t.message) }
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
                                b.putFeatureSpecifiedFeatureCalculatorForPath(
                                        fsfc.featurePath,
                                        fsfc
                                    )
                                    .putFeatureNameForPath(fsfc.featureName, fsfc.featurePath)
                            }
                    }
                }
                .onErrorMap { t: Throwable ->
                    when (t) {
                        is ServiceError -> {
                            t
                        }
                        else -> {
                            ServiceError.builder()
                                .message(
                                    "calculate_feature_specified_feature_calculators: [ status: failed ]"
                                )
                                .cause(t)
                                .build()
                        }
                    }
                }
                .doOnError { t: Throwable -> logger.error("{}", t.message) }
        }
    }

    private fun calculateAttributeCoordinatesRegistry():
        (MaterializationMetamodelBuildContext) -> Mono<MaterializationMetamodelBuildContext> {
        return { mmbc: MaterializationMetamodelBuildContext ->
            Mono.fromCallable {
                    mmbc.update {
                        aliasCoordinatesRegistry(
                            AliasCoordinatesRegistryCreator.invoke(
                                mmbc.materializationGraphQLSchema
                            )
                        )
                    }
                }
                .onErrorMap { t: Throwable ->
                    when (t) {
                        is ServiceError -> {
                            t
                        }
                        else -> {
                            ServiceError.builder()
                                .message(
                                    "calculate_attribute_coordinates_registry: [ status: failed ]"
                                )
                                .cause(t)
                                .build()
                        }
                    }
                }
                .doOnError { t: Throwable -> logger.error("{}", t.message) }
        }
    }
}
