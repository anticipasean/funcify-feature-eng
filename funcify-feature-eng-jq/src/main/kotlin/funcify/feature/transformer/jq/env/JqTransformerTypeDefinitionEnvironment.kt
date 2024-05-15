package funcify.feature.transformer.jq.env

import funcify.feature.transformer.jq.JqTransformer

/**
 * @author smccarron
 * @created 2023-07-06
 */
interface JqTransformerTypeDefinitionEnvironment {

    companion object {
        const val DEFAULT_INPUT_ARGUMENT_NAME = "input"
        const val DEFAULT_DESCRIPTION_FORMAT = "jq [ expression: \"%s\" ]"
        const val DEFAULT_JQ_TRANSFORMER_FIELD_NAME = "jq"
        const val DEFAULT_JQ_TRANSFORMER_OBJECT_TYPE_NAME = "Jq"
        const val TRANSFORMER_FIELD_NAME = "transformer"
        const val TRANSFORMER_OBJECT_TYPE_NAME = "Transformer"
        const val QUERY_OBJECT_TYPE_NAME = "Query"
    }

    val transformerSourceName: String

    val jqTransformers: List<JqTransformer>
}
