package funcify.feature.schema.limit

/**
 * @author smccarron
 * @created 2023-08-26
 */
interface ModelLimits {

    companion object {
        /**
         * 1 (ElementType: "dataElement"|"feature"|"transformer") + 1 (domain: "show") + 1 (field on
         * domain: "showId") = 3
         */
        const val REQUIRED_MINIMUM_OPERATION_DEPTH: Int = 3

        const val DEFAULT_MAXIMUM_OPERATION_DEPTH: Int = 7

        private val DEFAULT_INSTANCE: ModelLimits = DefaultModelLimits()

        fun getDefault(): ModelLimits {
            return DEFAULT_INSTANCE
        }
    }

    /**
     * The extent to which an operation may be nested:
     * ```
     * query {
     *     dataElement {
     *         show(showId: 123) {
     *             title
     *             cast {
     *                 name
     *                 filmography {
     *                     title
     *                     releaseYear
     *                 }
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * e.g. gqlo:/dataElement/show/cast/filmography/title: level of nesting == 5
     *
     * Deeply nested operations are typically less performant than _flattened_ counterparts, so a
     * limit placed on operations enables a lot of optimization to be performed on the _supported_
     * operation depth
     */
    val maximumOperationDepth: Int

    // TODO: Could add support for query complexity limits if found advantageous in generating
    // request materialization graph

    // TODO: Could also add support for limits like field name length, field naming conventions,
    // name uniqueness across element types
}
