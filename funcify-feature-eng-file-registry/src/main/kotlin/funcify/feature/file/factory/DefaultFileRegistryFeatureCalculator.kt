package funcify.feature.file.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.file.FileRegistryFeatureCalculator
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import graphql.language.SDLDefinition
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultFileRegistryFeatureCalculator(
    override val name: String,
    override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>
) : FileRegistryFeatureCalculator {

    companion object {

        private class DefaultFeatureCalculatorCallableBuilder(
            private var featureFieldCoordinates: FieldCoordinates? = null,
            private var featurePath: GQLOperationPath? = null,
            private var featureGraphQLFieldDefinition: GraphQLFieldDefinition? = null,
            private val transformerCallables: PersistentList.Builder<TransformerCallable> =
                persistentListOf<TransformerCallable>().builder()
        ) : FeatureCalculatorCallable.Builder {

            override fun selectFeature(
                coordinates: FieldCoordinates,
                path: GQLOperationPath,
                graphQLFieldDefinition: GraphQLFieldDefinition,
            ): FeatureCalculatorCallable.Builder =
                this.apply {
                    featureFieldCoordinates = coordinates
                    featurePath = path
                    featureGraphQLFieldDefinition = graphQLFieldDefinition
                }

            override fun addTransformerCallable(
                transformerCallable: TransformerCallable
            ): FeatureCalculatorCallable.Builder =
                this.apply { transformerCallables.add(transformerCallable) }

            override fun build(): FeatureCalculatorCallable {
                return eagerEffect<String, FeatureCalculatorCallable> {
                        ensureNotNull(featureFieldCoordinates) {
                            "feature_field_coordinates not provided"
                        }
                        ensureNotNull(featurePath) { "feature_path not provided" }
                        ensureNotNull(featureGraphQLFieldDefinition) {
                            "feature_graphql_field_definition not provided"
                        }
                        DefaultFeatureCalculatorCallable(
                            featureCoordinates = featureFieldCoordinates!!,
                            featurePath = featurePath!!,
                            featureGraphQLFieldDefinition = featureGraphQLFieldDefinition!!,
                            transformerCallablesByPath =
                                transformerCallables
                                    .build()
                                    .asSequence()
                                    .map { tc: TransformerCallable -> tc.transformerPath to tc }
                                    .reducePairsToPersistentMap()
                        )
                    }
                    .fold(
                        { message: String ->
                            throw ServiceError.of(
                                "unable to create %s [ message: %s ]",
                                DefaultFeatureCalculatorCallable::class.simpleName,
                                message
                            )
                        },
                        ::identity
                    )
            }
        }

        private class DefaultFeatureCalculatorCallable(
            override val featureCoordinates: FieldCoordinates,
            override val featurePath: GQLOperationPath,
            override val featureGraphQLFieldDefinition: GraphQLFieldDefinition,
            override val transformerCallablesByPath:
                ImmutableMap<GQLOperationPath, TransformerCallable>
        ) : FeatureCalculatorCallable {

            override val argumentsByName: ImmutableMap<String, GraphQLArgument> by lazy {
                featureGraphQLFieldDefinition.arguments
                    .asSequence()
                    .map { a: GraphQLArgument -> a.name to a }
                    .reducePairsToPersistentMap()
            }

            override val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument> by lazy {
                featureGraphQLFieldDefinition.arguments
                    .asSequence()
                    .map { a: GraphQLArgument -> featurePath.transform { argument(a.name) } to a }
                    .reducePairsToPersistentMap()
            }

            companion object {
                private val METHOD_TAG: String =
                    StandardNamingConventions.SNAKE_CASE.deriveName(
                            DefaultFeatureCalculatorCallable::class.simpleName ?: "<NA>"
                        )
                        .qualifiedForm + ".invoke"
                private val logger: Logger = loggerFor<DefaultFeatureCalculatorCallable>()
            }

            override fun invoke(
                trackableFeatureValue: TrackableValue<JsonNode>,
                arguments: ImmutableMap<GQLOperationPath, Mono<JsonNode>>,
            ): Mono<TrackableValue<JsonNode>> {
                logger.info("{}: []", METHOD_TAG)
                return Mono.error { ServiceError.of("$METHOD_TAG not yet implemented") }
            }
        }
    }

    override fun builder(): FeatureCalculatorCallable.Builder {
        return DefaultFeatureCalculatorCallableBuilder()
    }
}
