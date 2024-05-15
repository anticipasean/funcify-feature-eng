package funcify.feature.transformer.jq.source.callable

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger

internal class DefaultJqTransformerCallableBuilder(
    private val jqTransformersByName: ImmutableMap<String, JqTransformer>,
    private var transformerSpecifiedTransformerSource: TransformerSpecifiedTransformerSource? =
        null,
) : TransformerCallable.Builder {

    companion object {
        private val logger: Logger = loggerFor<DefaultJqTransformerCallableBuilder>()
    }

    override fun selectTransformer(
        transformerSpecifiedTransformerSource: TransformerSpecifiedTransformerSource
    ): TransformerCallable.Builder =
        this.apply {
            this.transformerSpecifiedTransformerSource = transformerSpecifiedTransformerSource
        }

    override fun build(): TransformerCallable {
        if (logger.isDebugEnabled) {
            logger.debug(
                "build: [ source.name: {}, transformer.field_coordinates: {} ]",
                transformerSpecifiedTransformerSource?.transformerSource?.name,
                transformerSpecifiedTransformerSource?.transformerFieldCoordinates
            )
        }
        return eagerEffect<String, TransformerCallable> {
                ensureNotNull(transformerSpecifiedTransformerSource) {
                    "transformer_specified_transformer_source not provided"
                }
                ensure(
                    transformerSpecifiedTransformerSource!!.transformerFieldCoordinates.fieldName in
                        jqTransformersByName
                ) {
                    """transformer_field_coordinates.field_name [ field_name: %s ] does not match 
                    |transformer_name within jq_transformers_by_name mappings"""
                        .flatten()
                        .format(
                            transformerSpecifiedTransformerSource
                                ?.transformerFieldCoordinates
                                ?.fieldName
                        )
                }
                DefaultJqTransformerCallable(
                    transformerSpecifiedTransformerSource = transformerSpecifiedTransformerSource!!,
                    jqTransformer =
                        jqTransformersByName[
                            transformerSpecifiedTransformerSource!!
                                .transformerFieldCoordinates
                                .fieldName!!]!!,
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
