package funcify.feature.materializer.graph.context

/**
 *
 * @author smccarron
 * @created 2023-08-13
 */
interface RequestMaterializationGraphContextFactory {

    fun expectedStandardJsonInputStandardQueryBuilder(): ExpectedStandardJsonInputStandardQuery.Builder

    fun expectedStandardJsonInputTabularQueryBuilder(): ExpectedStandardJsonInputTabularQuery.Builder

    fun expectedTabularInputStandardQueryBuilder(): ExpectedTabularInputStandardQuery.Builder

    fun expectedTabularInputTabularQueryBuilder(): ExpectedTabularInputTabularQuery.Builder

    fun standardQueryBuilder(): StandardQuery.Builder

    fun tabularQueryBuilder(): TabularQuery.Builder

}
