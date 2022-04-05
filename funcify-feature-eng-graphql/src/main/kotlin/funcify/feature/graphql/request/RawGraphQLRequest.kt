package funcify.feature.graphql.request

import graphql.execution.ExecutionId
import org.springframework.http.HttpHeaders
import java.net.URI
import java.util.Locale


/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface RawGraphQLRequest {

    val uri: URI

    val headers: HttpHeaders

    val rawGraphQLQueryText: String

    val operationName: String

    val variables: Map<String, Any?>

    val locale: Locale

    val requestId: String

    val executionInputCustomizers: List<GraphQLExecutionInputCustomizer>

    val executionId: ExecutionId

}