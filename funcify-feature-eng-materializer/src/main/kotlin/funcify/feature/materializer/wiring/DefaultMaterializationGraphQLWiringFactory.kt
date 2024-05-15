package funcify.feature.materializer.wiring

import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.type.MaterializationInterfaceSubtypeResolverFactory
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.ScalarTypeDefinition
import graphql.schema.DataFetcherFactory
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.TypeResolver
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.InterfaceWiringEnvironment
import graphql.schema.idl.ScalarWiringEnvironment
import org.slf4j.Logger

internal class DefaultMaterializationGraphQLWiringFactory(
    private val scalarTypeRegistry: ScalarTypeRegistry,
    private val dataFetcherFactory: DataFetcherFactory<*>,
    private val materializationInterfaceSubtypeResolverFactory:
        MaterializationInterfaceSubtypeResolverFactory
) : MaterializationGraphQLWiringFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphQLWiringFactory>()
    }

    override fun providesScalar(environment: ScalarWiringEnvironment): Boolean {
        logger.debug(
            """provides_scalar: [ 
            |environment.scalar_type_definition.name: {} 
            |]"""
                .flatten(),
            environment.scalarTypeDefinition.name
        )
        return environment.scalarTypeDefinition
            .toOption()
            .mapNotNull { sd: ScalarTypeDefinition ->
                scalarTypeRegistry.getScalarTypeDefinitionWithName(sd.name)
            }
            .isDefined()
    }

    override fun getScalar(environment: ScalarWiringEnvironment): GraphQLScalarType {
        return environment.scalarTypeDefinition
            .toOption()
            .mapNotNull { sd: ScalarTypeDefinition ->
                scalarTypeRegistry.getGraphQLScalarTypeWithName(sd.name)
            }
            .successIfDefined {
                val message =
                    """scalar_type expected for 
                        |[ scalar_type_definition.name: %s ] 
                        |but graphql_scalar_type not found 
                        |with that name"""
                        .flatten()
                        .format(environment.scalarTypeDefinition.name)
                ServiceError.builder().message(message).build()
            }
            .orElseThrow()
    }

    override fun providesTypeResolver(environment: InterfaceWiringEnvironment): Boolean {
        logger.debug(
            "provides_type_resolver: [ environment.interface_type_definition.name: {} ]",
            environment.interfaceTypeDefinition.name
        )
        return materializationInterfaceSubtypeResolverFactory.providesTypeResolver(environment)
    }

    override fun getTypeResolver(environment: InterfaceWiringEnvironment): TypeResolver {
        logger.debug(
            "get_type_resolver: [ environment.interface_type_definition.name: {} ]",
            environment.interfaceTypeDefinition.name
        )
        return materializationInterfaceSubtypeResolverFactory.createTypeResolver(environment)
    }

    override fun providesDataFetcherFactory(environment: FieldWiringEnvironment): Boolean {
        val graphQLFieldTypeName: String? =
            environment.fieldType?.let { got: GraphQLOutputType ->
                GraphQLTypeUtil.simplePrint(got)
            }
        logger.debug(
            """provides_data_fetcher_factory: [ environment: 
                |{ field_definition.name: {}, 
                |field_type: {} 
                |} ]"""
                .flatten(),
            environment.fieldDefinition.name,
            graphQLFieldTypeName
        )
        return true
    }

    override fun <T> getDataFetcherFactory(
        environment: FieldWiringEnvironment
    ): DataFetcherFactory<T> {
        val graphQLFieldTypeName: String? =
            environment.fieldType?.let { got: GraphQLOutputType ->
                GraphQLTypeUtil.simplePrint(got)
            }
        logger.debug(
            """get_data_fetcher_factory: [ environment: 
            |{ field_definition.name: %s, 
            |field_type: %s 
            |} ]"""
                .flatten()
                .format(environment.fieldDefinition?.name, graphQLFieldTypeName)
        )
        @Suppress("UNCHECKED_CAST") //
        val typedDataFetcherFactory: DataFetcherFactory<T> =
            dataFetcherFactory as DataFetcherFactory<T>
        return typedDataFetcherFactory
    }
}
