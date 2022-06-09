package funcify.feature.materializer.schema

import arrow.core.continuations.AtomicRef
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLSchema
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultMaterializationGraphQLSchemaBroker() : MaterializationGraphQLSchemaBroker {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphQLSchemaBroker>()
    }

    private val schemaAtTimestampHolder: AtomicRef<Pair<Long, GraphQLSchema>?> = AtomicRef()

    override fun pushNewMaterializationSchema(materializationSchema: GraphQLSchema) {
        logger.info(
            """push_new_materialization_schema: [ 
                |materialization_schema.query_type.field_definitions.size: 
                |${materializationSchema.queryType.fieldDefinitions.size} 
                |]""".flattenIntoOneLine()
        )
        val pushTime = System.currentTimeMillis()
        schemaAtTimestampHolder.getAndUpdate { storedTimestampAndSchema ->
            if (storedTimestampAndSchema == null) {
                pushTime to materializationSchema
            } else if (storedTimestampAndSchema.first < pushTime) {
                pushTime to materializationSchema
            } else {
                storedTimestampAndSchema
            }
        }
    }

    override fun fetchLatestMaterializationSchema(): Deferred<GraphQLSchema> {
        logger.info("fetch_latest_materialization_schema: []")
        return Deferred.fromMono(
            Mono.fromSupplier { ->
                when (val schema: GraphQLSchema? = schemaAtTimestampHolder.get()?.second) {
                    null -> {
                        throw MaterializerException(
                            MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                            "no graphql schema has been provided to this broker instance"
                        )
                    }
                    else -> {
                        schema
                    }
                }
            }
        )
    }
}
