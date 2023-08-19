package funcify.feature.materializer.schema

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * @author smccarron
 * @created 2023-08-19
 */
internal data class DefaultFeatureSpecifiedFeatureCalculator(
    override val featureName: String,
    override val featurePath: GQLOperationPath,
    override val featureFieldDefinition: GraphQLFieldDefinition,
    override val argumentsByPath: PersistentMap<GQLOperationPath, GraphQLArgument>,
    override val argumentsByName: PersistentMap<String, GraphQLArgument>,
    override val featureCalculator: FeatureCalculator
) : FeatureSpecifiedFeatureCalculator {

    companion object {

        fun builder(): FeatureSpecifiedFeatureCalculator.Builder {
            return DefaultBuilder()
        }

        internal class DefaultBuilder(
            private var featureName: String? = null,
            private var featurePath: GQLOperationPath? = null,
            private var featureFieldDefinition: GraphQLFieldDefinition? = null,
            private var argumentsByPath: PersistentMap.Builder<GQLOperationPath, GraphQLArgument> =
                persistentMapOf<GQLOperationPath, GraphQLArgument>().builder(),
            private var argumentsByName: PersistentMap.Builder<String, GraphQLArgument> =
                persistentMapOf<String, GraphQLArgument>().builder(),
            private var featureCalculator: FeatureCalculator? = null,
        ) : FeatureSpecifiedFeatureCalculator.Builder {

            override fun featureName(
                featureName: String
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.featureName = featureName }

            override fun featurePath(
                featurePath: GQLOperationPath
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.featurePath = featurePath }

            override fun featureFieldDefinition(
                featureFieldDefinition: GraphQLFieldDefinition
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.featureFieldDefinition = featureFieldDefinition }

            override fun putArgumentForPath(
                path: GQLOperationPath,
                argument: GraphQLArgument
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.argumentsByPath.put(path, argument) }

            override fun putAllPathArguments(
                pathArgumentPairs: Map<GQLOperationPath, GraphQLArgument>
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.argumentsByPath.putAll(pathArgumentPairs) }

            override fun putArgumentForName(
                name: String,
                argument: GraphQLArgument
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.argumentsByName.put(name, argument) }

            override fun putAllNameArguments(
                nameArgumentPairs: Map<String, GraphQLArgument>
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.argumentsByName.putAll(nameArgumentPairs) }

            override fun featureCalculator(
                featureCalculator: FeatureCalculator
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.featureCalculator = featureCalculator }

            override fun build(): FeatureSpecifiedFeatureCalculator {
                return eagerEffect<String, FeatureSpecifiedFeatureCalculator> {
                        ensureNotNull(featurePath) { "featurePath is null" }
                        ensureNotNull(featureFieldDefinition) { "featureFieldDefinition is null" }
                        ensureNotNull(featureCalculator) { "featureCalculator is null" }
                        DefaultFeatureSpecifiedFeatureCalculator(
                            featureName = featureName ?: featureFieldDefinition!!.name,
                            featurePath = featurePath!!,
                            featureFieldDefinition = featureFieldDefinition!!,
                            argumentsByPath = argumentsByPath.build(),
                            argumentsByName = argumentsByName.build(),
                            featureCalculator = featureCalculator!!
                        )
                    }
                    .fold(
                        { message: String ->
                            throw ServiceError.of(
                                "unable to create instance of [ type: %s ] due to [ message: %s ]",
                                DomainSpecifiedDataElementSource::class.qualifiedName,
                                message
                            )
                        },
                        ::identity
                    )
            }
        }
    }
}
