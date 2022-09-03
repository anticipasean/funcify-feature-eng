package funcify.feature.materializer.session

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.schema.MaterializationMetamodelBroker
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
internal class DefaultGraphQLSingleRequestSessionFactory(
    private val materializationMetamodelBroker: MaterializationMetamodelBroker
) : GraphQLSingleRequestSessionFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLSingleRequestSessionFactory>()
    }

    override fun createSessionForSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): Mono<out GraphQLSingleRequestSession> {
        logger.info(
            """create_session_for_single_request: 
                |[ raw_graphql_request.request_id: ${rawGraphQLRequest.requestId} ]
                |""".flatten()
        )
        return materializationMetamodelBroker
            .fetchLatestMaterializationMetamodel()
            .map { materializationMetamodel ->
                DefaultGraphQLSingleRequestSession(
                    materializationSchema = materializationMetamodel.materializationGraphQLSchema,
                    metamodelGraph = materializationMetamodel.metamodelGraph,
                    rawGraphQLRequest = rawGraphQLRequest
                )
            }
            .toMono()
    }
}
