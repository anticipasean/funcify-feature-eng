package funcify.feature.datasource.retrieval

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface SchematicPathBasedJsonRetrievalFunctionFactory {

    fun multipleSourceIndicesJsonRetrievalFunctionBuilder():
        MultipleSourceIndicesJsonRetrievalFunction.Builder

    fun singleSourceIndexCacheRetrievalFunctionBuilder():
        SingleSourceIndexJsonOptionCacheRetrievalFunction.Builder
}
