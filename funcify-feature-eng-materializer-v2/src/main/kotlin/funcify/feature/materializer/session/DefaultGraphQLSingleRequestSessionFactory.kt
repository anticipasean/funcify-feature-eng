package funcify.feature.materializer.session

import funcify.feature.materializer.input.SingleRequestRawInputContextExtractor
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.schema.MaterializationMetamodelBroker
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2/20/22
 */
internal class DefaultGraphQLSingleRequestSessionFactory(
    private val materializationMetamodelBroker: MaterializationMetamodelBroker,
    private val singleRequestRawInputContextExtractor: SingleRequestRawInputContextExtractor
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
                |"""
                .flatten()
        )
        return materializationMetamodelBroker
            .fetchLatestMaterializationMetamodel()
            .map { mm: MaterializationMetamodel ->
                DefaultGraphQLSingleRequestSession(
                    materializationMetamodel = mm,
                    rawGraphQLRequest = rawGraphQLRequest
                )
            }
            .flatMap { s: GraphQLSingleRequestSession ->
                singleRequestRawInputContextExtractor.extractRawInputContextIfProvided(s)
            }
    }
}
