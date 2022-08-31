package funcify.feature.spring.session

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.schema.MaterializationMetamodelBroker
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.GraphQLSingleRequestSessionFactory
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
internal class SpringGraphQLSingleRequestSessionFactory(
    private val materializationMetamodelBroker: MaterializationMetamodelBroker
) : GraphQLSingleRequestSessionFactory {

    companion object {
        private val logger: Logger = loggerFor<SpringGraphQLSingleRequestSessionFactory>()
    }

    override fun createSessionForSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): KFuture<GraphQLSingleRequestSession> {
        logger.info(
            """create_session_for_single_request: 
                |[ raw_graphql_request.request_id: ${rawGraphQLRequest.requestId} ]
                |""".flatten()
        )
        return materializationMetamodelBroker.fetchLatestMaterializationMetamodel().map {
            materializationMetamodel ->
            DefaultSpringGraphQLSingleRequestSession(
                materializationSchema = materializationMetamodel.materializationGraphQLSchema,
                metamodelGraph = materializationMetamodel.metamodelGraph,
                rawGraphQLRequest = rawGraphQLRequest
            )
        }
    }
}
