package funcify.feature.materializer.schema

import arrow.core.*
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.TypeDefinition
import graphql.schema.*
import java.util.*
import org.slf4j.Logger

internal object MaterializationMetamodelFactGatherer :
    (FeatureEngineeringModel, GraphQLSchema) -> MaterializationMetamodelFacts {

    private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
    private val logger: Logger = loggerFor<MaterializationMetamodelFactGatherer>()

    override fun invoke(
        featureEngineeringModel: FeatureEngineeringModel,
        graphQLSchema: GraphQLSchema
    ): MaterializationMetamodelFacts {
        logger.info(
            """gather_facts_on_materialization_metamodel: 
            |[ feature_engineering_model.query_object_type.name: {}, 
            |graphql_schema.query_type.name: {} ]"""
                .flatten(),
            featureEngineeringModel.typeDefinitionRegistry
                .getType("Query")
                .toOption()
                .map(TypeDefinition<*>::getName)
                .getOrElse { "<NA>" },
            graphQLSchema.queryType.name
        )
        return Some(DefaultMaterializationMetamodelFacts.empty())
            .map(calculatePathsAndFieldCoordinates(graphQLSchema))
            .map(calculateDomainSpecifiedDataElementSources(featureEngineeringModel, graphQLSchema))
            .map(
                calculateFeatureSpecifiedFeatureCalculators(featureEngineeringModel, graphQLSchema)
            )
            .getOrElse { DefaultMaterializationMetamodelFacts.empty() }
            .also { mmf: MaterializationMetamodelFacts ->
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
            }
    }

    private fun calculatePathsAndFieldCoordinates(
        graphQLSchema: GraphQLSchema
    ): (MaterializationMetamodelFacts) -> MaterializationMetamodelFacts {
        return { mmf: MaterializationMetamodelFacts ->
            PathCoordinatesGatherer.invoke(mmf, graphQLSchema)
        }
    }

    private fun calculateDomainSpecifiedDataElementSources(
        featureEngineeringModel: FeatureEngineeringModel,
        graphQLSchema: GraphQLSchema
    ): (MaterializationMetamodelFacts) -> MaterializationMetamodelFacts {
        return { mmf: MaterializationMetamodelFacts ->
            mmf.update {
                DomainSpecifiedDataElementSourceCreator.invoke(
                        featureEngineeringModel,
                        graphQLSchema
                    )
                    .fold(this) {
                        b: MaterializationMetamodelFacts.Builder,
                        dsdes: DomainSpecifiedDataElementSource ->
                        b.putDomainSpecifiedDataElementSourceForPath(dsdes.domainPath, dsdes)
                    }
            }
        }
    }

    private fun calculateFeatureSpecifiedFeatureCalculators(
        featureEngineeringModel: FeatureEngineeringModel,
        graphQLSchema: GraphQLSchema
    ): (MaterializationMetamodelFacts) -> MaterializationMetamodelFacts {
        return { mmf: MaterializationMetamodelFacts ->
            mmf.update {
                FeatureSpecifiedFeatureCalculatorCreator.invoke(
                        featureEngineeringModel,
                        graphQLSchema
                    )
                    .fold(this) {
                        b: MaterializationMetamodelFacts.Builder,
                        fsfc: FeatureSpecifiedFeatureCalculator ->
                        b.putFeatureSpecifiedFeatureCalculatorForPath(fsfc.featurePath, fsfc)
                    }
            }
        }
    }
}
