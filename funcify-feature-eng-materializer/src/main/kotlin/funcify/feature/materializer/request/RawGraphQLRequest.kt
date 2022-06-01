package funcify.feature.materializer.request

import graphql.execution.ExecutionId
import java.net.URI
import java.util.*
import org.springframework.http.HttpHeaders

/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface RawGraphQLRequest {

    val requestId: String

    val executionId: ExecutionId

    val uri: URI

    val headers: HttpHeaders

    val rawGraphQLQueryText: String

    val operationName: String

    val variables: Map<String, Any?>

    val locale: Locale

    val executionInputCustomizers: List<GraphQLExecutionInputCustomizer>

}
