package funcify.feature.materializer.graph

/**
 * @author smccarron
 * @created 2023-08-01
 */
enum class MaterializationEdge {
    DEFAULT_ARGUMENT_VALUE_PROVIDED,
    DIRECT_ARGUMENT_VALUE_PROVIDED,
    VARIABLE_VALUE_PROVIDED,
    RAW_INPUT_VALUE_PROVIDED,
    EXTRACT_FROM_SOURCE,
    ELEMENT_TYPE
}
