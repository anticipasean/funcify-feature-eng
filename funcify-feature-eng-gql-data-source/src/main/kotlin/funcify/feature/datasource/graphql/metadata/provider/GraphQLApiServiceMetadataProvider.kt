package funcify.feature.datasource.graphql.metadata.provider

import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import funcify.feature.tools.json.MappingTarget.Companion.toKotlinObject
import graphql.GraphQLError
import graphql.introspection.IntrospectionResultToSchema
import graphql.language.Definition
import graphql.language.Document
import graphql.language.SDLDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentList
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class GraphQLApiServiceMetadataProvider(private val jsonMapper: JsonMapper) :
    GraphQLApiMetadataProvider<GraphQLApiService> {

    companion object {
        private const val GRAPHQL_RESPONSE_DATA_KEY = "data"
        private const val GRAPHQL_RESPONSE_ERRORS_KEY = "errors"
        private val logger: Logger = loggerFor<GraphQLApiServiceMetadataProvider>()
    }

    override fun provideTypeDefinitionRegistry(
        resource: GraphQLApiService
    ): Mono<TypeDefinitionRegistry> {
        val methodTag: String = """provide_type_definition_registry"""
        val service: GraphQLApiService = resource
        logger.debug(
            """$methodTag: [ service: 
                |{ name: ${service.serviceName}, 
                |host: ${service.hostName}, 
                |port: ${service.port}, 
                |context_path: ${service.serviceContextPath} } ]
                |"""
                .flatten()
        )

        return service
            .executeSingleQuery(service.metadataQuery)
            .flatMap { jn: JsonNode ->
                when {
                    jn.has(GRAPHQL_RESPONSE_DATA_KEY) -> {
                        Mono.justOrEmpty(jn.get(GRAPHQL_RESPONSE_DATA_KEY))
                    }
                    jn.has(GRAPHQL_RESPONSE_ERRORS_KEY) -> {
                        Mono.error {
                            val errorsNode: JsonNode = jn.get(GRAPHQL_RESPONSE_ERRORS_KEY)
                            ServiceError.downstreamResponseErrorBuilder()
                                .message("reported_graphql_errors: [ $errorsNode ]")
                                .build()
                        }
                    }
                    else -> {
                        Mono.error {
                            val message =
                                """json_node is not in expected format 
                                    |i.e. has element with key [ "data" ]"""
                                    .flatten()
                            ServiceError.downstreamResponseErrorBuilder().message(message).build()
                        }
                    }
                }
            }
            .flatMap { schemaNode: JsonNode ->
                convertJsonNodeIntoGraphQLSchemaInstance(schemaNode).toMono()
            }
            .doOnError { t: Throwable ->
                logger.error(
                    "$methodTag: [ status: failed ][ type: {}, message: {} ]",
                    t::class.qualifiedName,
                    t.message
                )
            }
    }

    private fun convertJsonNodeIntoGraphQLSchemaInstance(
        schemaJsonNode: JsonNode
    ): Try<TypeDefinitionRegistry> {
        return Try.success(schemaJsonNode)
            .flatMap { jn: JsonNode ->
                jsonMapper.fromJsonNode(jn).toKotlinObject<Map<String, Any?>>()
            }
            .map { strMap: Map<String, Any?> ->
                IntrospectionResultToSchema().createSchemaDefinition(strMap)
            }
            .map { document: Document ->
                document.definitions
                    .stream()
                    .map { def: Definition<*> ->
                        def.toOption().filterIsInstance<SDLDefinition<*>>()
                    }
                    .flatMapOptions()
                    .reduceToPersistentList()
            }
            .map { sdlDefinitions: PersistentList<SDLDefinition<*>> ->
                TypeDefinitionRegistry().apply {
                    addAll(sdlDefinitions).ifPresent { gqlerror: GraphQLError ->
                        ServiceError.builder()
                            .message("error during type_definition_registry creation")
                            .cause(gqlerror as? Throwable)
                            .build()
                    }
                }
            }
    }
}
