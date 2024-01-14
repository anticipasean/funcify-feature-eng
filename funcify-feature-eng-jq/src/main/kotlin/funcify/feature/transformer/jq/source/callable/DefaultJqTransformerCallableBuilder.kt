package funcify.feature.transformer.jq.source.callable

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import arrow.core.lastOrNone
import funcify.feature.error.ServiceError
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger

internal class DefaultJqTransformerCallableBuilder(
    private val sourceName: String,
    private val jqTransformersByName: ImmutableMap<String, JqTransformer>,
    private var transformerFieldCoordinates: FieldCoordinates? = null,
    private var transformerPath: GQLOperationPath? = null,
    private var transformerGraphQLFieldDefinition: GraphQLFieldDefinition? = null
) : TransformerCallable.Builder {

    companion object {
        private val logger: Logger = loggerFor<DefaultJqTransformerCallableBuilder>()
    }

    override fun selectTransformer(
        coordinates: FieldCoordinates,
        path: GQLOperationPath,
        graphQLFieldDefinition: GraphQLFieldDefinition,
    ): TransformerCallable.Builder =
        this.apply {
            this.transformerFieldCoordinates = coordinates
            this.transformerPath = path
            this.transformerGraphQLFieldDefinition = graphQLFieldDefinition
        }

    override fun build(): TransformerCallable {
        if (logger.isDebugEnabled) {
            logger.debug(
                "build: [ source.name: {}, transformer.field_coordinates: {} ]",
                sourceName,
                transformerFieldCoordinates
            )
        }
        return eagerEffect<String, TransformerCallable> {
                ensureNotNull(transformerFieldCoordinates) {
                    "transformer_field_coordinates not provided"
                }
                ensureNotNull(transformerPath) { "transformer_path not provided" }
                ensureNotNull(transformerGraphQLFieldDefinition) {
                    "transformer_graphql_field_definition not provided"
                }
                ensure(
                    transformerFieldCoordinates!!.fieldName ==
                        transformerGraphQLFieldDefinition!!.name
                ) {
                    """transformer_field_coordinates.field_name does not match 
                        |transformer_graphql_field_definition.name"""
                        .flatten()
                }
                ensure(
                    transformerPath!!
                        .selection
                        .lastOrNone()
                        .mapNotNull { ss: SelectionSegment ->
                            when (ss) {
                                is FieldSegment -> ss.fieldName
                                is AliasedFieldSegment -> ss.fieldName
                                is FragmentSpreadSegment -> ss.selectedField.fieldName
                                is InlineFragmentSegment -> ss.selectedField.fieldName
                            }
                        }
                        .filter { fn: String -> fn == transformerFieldCoordinates!!.fieldName }
                        .isDefined()
                ) {
                    """transformer_path[-1].field_name does not match 
                    |transformer_field_coordinates.field_name"""
                        .flatten()
                }
                ensure(transformerFieldCoordinates!!.fieldName in jqTransformersByName) {
                    """transformer_field_coordinates.field_name [ field_name: %s ] does not match 
                    |transformer_name within jq_transformers_by_name mappings"""
                        .flatten()
                        .format(transformerFieldCoordinates?.fieldName)
                }

                DefaultJqTransformerCallable(
                    sourceName = sourceName,
                    jqTransformer = jqTransformersByName[transformerFieldCoordinates!!.fieldName]!!,
                    transformerFieldCoordinates = transformerFieldCoordinates!!,
                    transformerPath = transformerPath!!,
                    transformerGraphQLFieldDefinition = transformerGraphQLFieldDefinition!!
                )
            }
            .fold(
                { message: String ->
                    throw ServiceError.of(
                        "unable to create %s [ message: %s ]",
                        DefaultJqTransformerCallable::class.simpleName,
                        message
                    )
                },
                ::identity
            )
    }
}
