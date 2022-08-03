package funcify.feature.spring.session

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.schema.MaterializationGraphQLSchemaBroker
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.GraphQLSingleRequestSessionFactory
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLSchema
import org.slf4j.Logger
import org.springframework.stereotype.Component

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
@Component
class SpringGraphQLSingleRequestSessionFactory(
    private val materializationGraphQLSchemaBroker: MaterializationGraphQLSchemaBroker
) : GraphQLSingleRequestSessionFactory {

    companion object {
        private val logger: Logger = loggerFor<SpringGraphQLSingleRequestSessionFactory>()
    }

    override fun createSessionForSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): Deferred<GraphQLSingleRequestSession> {
        logger.info(
            """create_session_for_single_request: 
                |[ raw_graphql_request.request_id: ${rawGraphQLRequest.requestId} ]
                |""".flattenIntoOneLine()
        )
        return materializationGraphQLSchemaBroker.fetchLatestMaterializationSchema().map {
            gqlSchema: GraphQLSchema ->
            DefaultSpringGraphQLSingleRequestSession(
                materializationSchema = gqlSchema,
                rawGraphQLRequest = rawGraphQLRequest
            )
        }
    }
}
