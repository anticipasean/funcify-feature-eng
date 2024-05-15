package funcify.feature.transformer.jq.env

import funcify.feature.transformer.jq.JqTransformer

internal data class DefaultJacksonJqTypeDefinitionEnvironment(
    override val transformerSourceName: String,
    override val jqTransformers: List<JqTransformer>
) : JqTransformerTypeDefinitionEnvironment
