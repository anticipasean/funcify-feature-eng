package funcify.feature.materializer.wiring

import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ErrorType
import graphql.GraphqlErrorException
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactory
import graphql.schema.GraphQLScalarType
import graphql.schema.TypeResolver
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.InterfaceWiringEnvironment
import graphql.schema.idl.ScalarWiringEnvironment
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import graphql.schema.idl.UnionWiringEnvironment
import graphql.schema.idl.WiringFactory

interface MaterializationGraphQLWiringFactory : WiringFactory {

    companion object {

        private fun <T : Any> notExpectingAnyCallErrorHandler(methodName: String): T {
            throw GraphqlErrorException.newErrorException()
                .errorClassification(ErrorType.OperationNotSupported)
                .message(
                    """materialization_graphql_wiring_factory: 
                      |method [ name: ${methodName} ] called 
                      |but has not been or will not be implemented
                      |"""
                        .flatten()
                )
                .build()
        }
    }

    override fun providesScalar(environment: ScalarWiringEnvironment): Boolean {
        return false
    }

    override fun getScalar(environment: ScalarWiringEnvironment): GraphQLScalarType {
        return notExpectingAnyCallErrorHandler("get_scalar")
    }

    override fun providesTypeResolver(environment: InterfaceWiringEnvironment): Boolean {
        return false
    }

    override fun providesTypeResolver(environment: UnionWiringEnvironment): Boolean {
        return false
    }

    override fun getTypeResolver(environment: InterfaceWiringEnvironment): TypeResolver {
        return notExpectingAnyCallErrorHandler("get_type_resolver(interface_wiring_environment)")
    }

    override fun getTypeResolver(environment: UnionWiringEnvironment): TypeResolver {
        return notExpectingAnyCallErrorHandler("get_type_resolver(union_wiring_environment)")
    }

    override fun providesDataFetcherFactory(environment: FieldWiringEnvironment): Boolean {
        return false
    }

    override fun <T : Any?> getDataFetcherFactory(
        environment: FieldWiringEnvironment
    ): DataFetcherFactory<T> {
        return notExpectingAnyCallErrorHandler("get_data_fetcher_factory")
    }

    override fun providesSchemaDirectiveWiring(
        environment: SchemaDirectiveWiringEnvironment<*>
    ): Boolean {
        return false
    }

    override fun getSchemaDirectiveWiring(
        environment: SchemaDirectiveWiringEnvironment<*>
    ): SchemaDirectiveWiring {
        return notExpectingAnyCallErrorHandler("get_schema_directive_wiring")
    }

    override fun providesDataFetcher(environment: FieldWiringEnvironment): Boolean {
        return false
    }

    override fun getDataFetcher(environment: FieldWiringEnvironment): DataFetcher<*> {
        return notExpectingAnyCallErrorHandler("get_data_fetcher")
    }

    override fun getDefaultDataFetcher(environment: FieldWiringEnvironment): DataFetcher<*>? {
        return null
    }
}
