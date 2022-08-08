package funcify.feature.materializer.service

import arrow.core.filterIsInstance
import arrow.core.toOption
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationDataFetcherFactory
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.DataFetcherFactory
import graphql.schema.GraphQLNamedOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.ScalarWiringEnvironment
import org.slf4j.Logger

internal class DefaultMaterializationGraphQLWiringFactory(
    private val scalarTypeRegistry: ScalarTypeRegistry,
    private val singleRequestFieldMaterializationDataFetcherFactory:
        SingleRequestFieldMaterializationDataFetcherFactory
) : MaterializationGraphQLWiringFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphQLWiringFactory>()
    }

    override fun providesScalar(environment: ScalarWiringEnvironment): Boolean {
        logger.debug(
            """provides_scalar: [ 
            |environment.scalar_type_definition.name: ${environment.scalarTypeDefinition.name} 
            |]""".flatten()
        )
        return environment.scalarTypeDefinition
            .toOption()
            .mapNotNull { def -> scalarTypeRegistry.getScalarTypeDefinitionWithName(def.name) }
            .isDefined()
    }

    override fun getScalar(environment: ScalarWiringEnvironment): GraphQLScalarType {
        return environment.scalarTypeDefinition
            .toOption()
            .mapNotNull { def -> scalarTypeRegistry.getGraphQLScalarTypeWithName(def.name) }
            .successIfDefined {
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """scalar_type expected for 
                        |[ scalar_type_definition.name: ${environment.scalarTypeDefinition.name} ] 
                        |but graphql_scalar_type not found 
                        |with that name""".flatten()
                )
            }
            .orElseThrow()
    }

    override fun providesDataFetcherFactory(environment: FieldWiringEnvironment): Boolean {
        val graphQLFieldTypeName =
            environment.fieldType
                .toOption()
                .filterIsInstance<GraphQLNamedOutputType>()
                .map(GraphQLNamedOutputType::getName)
                .orNull()
        logger.debug(
            """provides_data_fetcher_factory: [ environment: 
                |{ field_definition.name: ${environment.fieldDefinition.name}, 
                |field_type: $graphQLFieldTypeName 
                |} ]""".flatten()
        )
        return true
    }

    override fun <T> getDataFetcherFactory(
        environment: FieldWiringEnvironment
    ): DataFetcherFactory<T> {
        val graphQLFieldTypeName =
            environment.fieldType
                .toOption()
                .filterIsInstance<GraphQLNamedOutputType>()
                .map(GraphQLNamedOutputType::getName)
                .orNull()
        logger.debug(
            """get_data_fetcher_factory: [ environment: 
            |{ field_definition.name: ${environment.fieldDefinition?.name}, 
            |field_type: $graphQLFieldTypeName 
            |} ]""".flatten()
        )
        @Suppress("UNCHECKED_CAST") //
        val typedDataFetcherFactory: DataFetcherFactory<T> =
            singleRequestFieldMaterializationDataFetcherFactory as DataFetcherFactory<T>
        return typedDataFetcherFactory
    }
}
