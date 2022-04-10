package funcify.feature.materializer.request

import arrow.core.getOrElse
import arrow.core.toOption
import graphql.execution.ExecutionId
import org.springframework.http.HttpHeaders
import java.net.URI
import java.util.Locale


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
data class DefaultRawGraphQLRequest(override val uri: URI,
                                    override val headers: HttpHeaders,
                                    override val rawGraphQLQueryText: String = "",
                                    override val operationName: String = "",
                                    override val variables: Map<String, Any?> = mutableMapOf(),
                                    override val locale: Locale = Locale.getDefault(),
                                    override val requestId: String = "",
                                    override val executionInputCustomizers: List<GraphQLExecutionInputCustomizer> = mutableListOf()) : RawGraphQLRequest {

    override val executionId: ExecutionId = requestId.toOption()
            .filterNot(String::isEmpty)
            .map(ExecutionId::from)
            .getOrElse { ExecutionId.generate() }

}
