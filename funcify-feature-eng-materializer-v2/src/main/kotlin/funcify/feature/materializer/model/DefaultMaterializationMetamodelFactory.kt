package funcify.feature.materializer.model

import funcify.feature.schema.FeatureEngineeringModel
import graphql.schema.GraphQLSchema
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-21
 */
internal class DefaultMaterializationMetamodelFactory(
    private val materializationMetamodelBuildStrategy: MaterializationMetamodelBuildStrategy
) : MaterializationMetamodelFactory {

    companion object {
        internal class DefaultBuilder(
            private val materializationMetamodelBuildStrategy:
                MaterializationMetamodelBuildStrategy,
            private var featureEngineeringModel: FeatureEngineeringModel? = null,
            private var materializationGraphQLSchema: GraphQLSchema? = null
        ) : MaterializationMetamodel.Builder {

            override fun featureEngineeringModel(
                featureEngineeringModel: FeatureEngineeringModel
            ): MaterializationMetamodel.Builder =
                this.apply { this.featureEngineeringModel = featureEngineeringModel }

            override fun materializationGraphQLSchema(
                materializationGraphQLSchema: GraphQLSchema
            ): MaterializationMetamodel.Builder =
                this.apply { this.materializationGraphQLSchema = materializationGraphQLSchema }

            override fun build(): Mono<out MaterializationMetamodel> {
                return Mono.fromCallable {
                        requireNotNull(featureEngineeringModel) { "featureEngineeringModel has not been provided" }
                        requireNotNull(materializationGraphQLSchema) {
                            "materializationGraphQLSchema has not been provided"
                        }
                        DefaultMaterializationMetamodelBuildContext.createInitial(
                            featureEngineeringModel!!,
                            materializationGraphQLSchema!!
                        )
                    }
                    .flatMap { mmbc: MaterializationMetamodelBuildContext ->
                        materializationMetamodelBuildStrategy.buildMaterializationMetamodel(mmbc)
                    }
            }
        }
    }

    override fun builder(): MaterializationMetamodel.Builder {
        return DefaultBuilder(
            materializationMetamodelBuildStrategy = materializationMetamodelBuildStrategy
        )
    }
}
