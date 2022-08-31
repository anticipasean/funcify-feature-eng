package funcify.feature.datasource.graphql.metadata.provider

import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.GraphQLError
import graphql.introspection.IntrospectionResultToSchema
import graphql.language.Definition
import graphql.language.Document
import graphql.language.SDLDefinition
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentList
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class DefaultGraphQLApiSourceMetadataProvider(private val objectMapper: ObjectMapper) :
    GraphQLApiSourceMetadataProvider {

    companion object {
        private const val GRAPHQL_RESPONSE_DATA_KEY = "data"
        private const val GRAPHQL_RESPONSE_ERRORS_KEY = "errors"
        private val logger: Logger =
            LoggerFactory.getLogger(DefaultGraphQLApiSourceMetadataProvider::class.java)
    }

    override fun provideMetadata(service: GraphQLApiService): KFuture<GraphQLSchema> {
        logger.debug(
            """provide_metadata: [ service: 
                |{ name: ${service.serviceName}, 
                |host: ${service.hostName}, 
                |port: ${service.port}, 
                |context_path: ${service.serviceContextPath} } ]
                |""".flatten()
        )
        return KFuture.fromAttempt(
            service
                .executeSingleQuery(service.metadataQuery)
                .get()
                .flatMap { jn: JsonNode ->
                    when {
                        jn.has(GRAPHQL_RESPONSE_DATA_KEY) -> {
                            Try.success(jn.get(GRAPHQL_RESPONSE_DATA_KEY))
                        }
                        jn.has(GRAPHQL_RESPONSE_ERRORS_KEY) -> {
                            Try.failure(
                                GQLDataSourceException(
                                    GQLDataSourceErrorResponse.CLIENT_ERROR,
                                    "reported_graphql_errors: [ ${jn.get(
                                        GRAPHQL_RESPONSE_ERRORS_KEY)} ]"
                                )
                            )
                        }
                        else -> {
                            val message =
                                "json_node is not in expected format i.e. has element with key [ \"data\" ]"
                            Try.failure(
                                GQLDataSourceException(
                                    GQLDataSourceErrorResponse.MALFORMED_CONTENT_RECEIVED,
                                    message
                                )
                            )
                        }
                    }
                }
                .flatMap { schemaNode: JsonNode ->
                    convertJsonNodeIntoGraphQLSchemaInstance(schemaNode)
                }
        )
    }

    private fun convertJsonNodeIntoGraphQLSchemaInstance(
        schemaJsonNode: JsonNode
    ): Try<GraphQLSchema> {
        return Try.success(schemaJsonNode)
            .map { jn: JsonNode -> objectMapper.treeToValue<Map<String, Any?>>(jn) }
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
                        throw GQLDataSourceException(
                            GQLDataSourceErrorResponse.Companion.GQLSpecificErrorResponse(gqlerror),
                            "error during type_definition_registry creation"
                        )
                    }
                }
            }
            .map { typeDefReg: TypeDefinitionRegistry ->
                SchemaGenerator().makeExecutableSchema(typeDefReg, RuntimeWiring.MOCKED_WIRING)
            }
    }
}
