package funcify.feature.materializer.graph.context

/**
 *
 * @author smccarron
 * @created 2023-08-13
 */
interface RequestMaterializationGraphContextFactory {

    fun standardQueryBuilder(): StandardQuery.Builder

    fun tabularQueryBuilder(): TabularQuery.Builder

}
