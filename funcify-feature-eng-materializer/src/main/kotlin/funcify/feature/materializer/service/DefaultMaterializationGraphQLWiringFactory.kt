package funcify.feature.materializer.service

import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationDataFetcherFactory
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.DataFetcherFactory
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.ScalarWiringEnvironment
import org.slf4j.Logger

internal class DefaultMaterializationGraphQLWiringFactory(
    private val singleRequestFieldMaterializationDataFetcherFactory:
        SingleRequestFieldMaterializationDataFetcherFactory
) : MaterializationGraphQLWiringFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphQLWiringFactory>()
    }

    override fun providesScalar(environment: ScalarWiringEnvironment): Boolean {
        return super.providesScalar(environment)
    }

    override fun getScalar(environment: ScalarWiringEnvironment): GraphQLScalarType {
        return super.getScalar(environment)
    }

    override fun <T> getDataFetcherFactory(
        environment: FieldWiringEnvironment
                                          ): DataFetcherFactory<T> {
        logger.debug(
            """get_data_fetcher_factory: [ environment: 
            |{ field_definition.name: ${environment.fieldDefinition?.name}, 
            |field_type: ${environment.fieldType} } ]""".flattenIntoOneLine()
        )
        @Suppress("UNCHECKED_CAST") //
        val typedDataFetcherFactory: DataFetcherFactory<T> =
            singleRequestFieldMaterializationDataFetcherFactory as DataFetcherFactory<T>
        return typedDataFetcherFactory
    }
}
