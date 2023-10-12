package funcify.feature.materializer.model

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.lastOrNone
import funcify.feature.error.ServiceError
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-09-05
 */
internal data class DefaultTransformerSpecifiedTransformerSource(
    override val transformerFieldCoordinates: FieldCoordinates,
    override val transformerPath: GQLOperationPath,
    override val transformerFieldDefinition: GraphQLFieldDefinition,
    override val transformerSource: TransformerSource,
) : TransformerSpecifiedTransformerSource {

    override val argumentsByName: ImmutableMap<String, GraphQLArgument> by lazy {
        transformerFieldDefinition.arguments
            .asSequence()
            .map { a: GraphQLArgument -> a.name to a }
            .reducePairsToPersistentMap()
    }
    override val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument> by lazy {
        argumentsByName
            .asSequence()
            .map { (n: String, a: GraphQLArgument) ->
                transformerPath.transform { argument(n) } to a
            }
            .reducePairsToPersistentMap()
    }
    override val argumentPathsByName: ImmutableMap<String, GQLOperationPath> by lazy {
        argumentsByPath
            .asSequence()
            .map { (p: GQLOperationPath, a: GraphQLArgument) -> a.name to p }
            .reducePairsToPersistentMap()
    }
    override val argumentsWithDefaultValuesByName: ImmutableMap<String, GraphQLArgument> by lazy {
        argumentsByName
            .asSequence()
            .filter { (_: String, a: GraphQLArgument) -> a.hasSetDefaultValue() }
            .reduceEntriesToPersistentMap()
    }
    override val argumentsWithoutDefaultValuesByName:
        ImmutableMap<String, GraphQLArgument> by lazy {
        argumentsByName
            .asSequence()
            .filter { (_: String, a: GraphQLArgument) -> !a.hasSetDefaultValue() }
            .reduceEntriesToPersistentMap()
    }
    override val argumentsWithoutDefaultValuesByPath:
        ImmutableMap<GQLOperationPath, GraphQLArgument> by lazy {
        argumentsByPath
            .asSequence()
            .filter { (_: GQLOperationPath, a: GraphQLArgument) -> !a.hasSetDefaultValue() }
            .reduceEntriesToPersistentMap()
    }
    companion object {

        fun builder(): TransformerSpecifiedTransformerSource.Builder {
            return DefaultBuilder()
        }

        private class DefaultBuilder(
            private var transformerFieldCoordinates: FieldCoordinates? = null,
            private var transformerPath: GQLOperationPath? = null,
            private var transformerFieldDefinition: GraphQLFieldDefinition? = null,
            private var transformerSource: TransformerSource? = null,
        ) : TransformerSpecifiedTransformerSource.Builder {

            override fun transformerFieldCoordinates(
                transformerFieldCoordinates: FieldCoordinates
            ): TransformerSpecifiedTransformerSource.Builder =
                this.apply { this.transformerFieldCoordinates = transformerFieldCoordinates }

            override fun transformerPath(
                transformerPath: GQLOperationPath
            ): TransformerSpecifiedTransformerSource.Builder =
                this.apply { this.transformerPath = transformerPath }

            override fun transformerFieldDefinition(
                transformerFieldDefinition: GraphQLFieldDefinition
            ): TransformerSpecifiedTransformerSource.Builder =
                this.apply { this.transformerFieldDefinition = transformerFieldDefinition }

            override fun transformerSource(
                transformerSource: TransformerSource
            ): TransformerSpecifiedTransformerSource.Builder =
                this.apply { this.transformerSource = transformerSource }

            override fun build(): TransformerSpecifiedTransformerSource {
                return eagerEffect<String, TransformerSpecifiedTransformerSource> {
                        ensureNotNull(transformerFieldCoordinates) {
                            "transformer_field_coordinates not provided"
                        }
                        ensureNotNull(transformerPath) { "transformer_path not provided" }
                        ensureNotNull(transformerFieldDefinition) {
                            "transformer_field_definition not provided"
                        }
                        ensureNotNull(transformerSource) { "transformer_source not provided" }
                        ensure(
                            transformerFieldCoordinates!!.fieldName ==
                                transformerFieldDefinition!!.name
                        ) {
                            "transformer_field_coordinates.field_name does not match transformer_field_definition.name"
                        }
                        ensure(!transformerPath!!.containsAliasForField()) {
                            "transformer_path cannot be aliased"
                        }
                        ensure(
                            transformerPath!!
                                .selection
                                .lastOrNone()
                                .filterIsInstance<FieldSegment>()
                                .map { fs: FieldSegment ->
                                    fs.fieldName == transformerFieldCoordinates!!.fieldName
                                }
                                .getOrElse { false }
                        ) {
                            "transformer_path[-1].field_name does not match transformer_field_coordinates.field_name"
                        }
                        DefaultTransformerSpecifiedTransformerSource(
                            transformerFieldCoordinates = transformerFieldCoordinates!!,
                            transformerPath = transformerPath!!,
                            transformerFieldDefinition = transformerFieldDefinition!!,
                            transformerSource = transformerSource!!,
                        )
                    }
                    .fold(
                        { message: String ->
                            throw ServiceError.of(
                                "unable to create %s [ message: %s ]",
                                TransformerSpecifiedTransformerSource::class.simpleName,
                                message
                            )
                        },
                        ::identity
                    )
            }
        }
    }
}
