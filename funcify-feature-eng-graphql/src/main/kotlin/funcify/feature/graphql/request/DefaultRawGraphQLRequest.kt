package funcify.feature.graphql.request

import arrow.core.getOrElse
import arrow.core.toOption
import graphql.execution.ExecutionId
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.util.UriComponents
import java.net.URI
import java.util.*


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
data class DefaultRawGraphQLRequest(override val uri: URI,
                                    override val headers: ServerRequest.Headers,
                                    override val rawGraphQLQueryText: String = "",
                                    override val operationName: String = "",
                                    override val variables: Map<String, Any> = mutableMapOf(),
                                    override val locale: Locale = Locale.getDefault(),
                                    override val requestId: String = "",
                                    override val executionInputCustomizers: List<GraphQLExecutionInputCustomizer> = mutableListOf()) : RawGraphQLRequest {

    override val executionId: ExecutionId = requestId.toOption()
            .filterNot(String::isEmpty)
            .map(ExecutionId::from)
            .getOrElse { ExecutionId.generate() }

}
