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

    interface Builder {

        fun requestId(requestId: String): Builder

        fun executionId(executionId: ExecutionId): Builder

        fun uri(uri: URI): Builder

        fun headers(headers: HttpHeaders): Builder

        fun rawGraphQLQueryText(rawGraphQLQueryText: String): Builder

        fun operationName(operationName: String): Builder

        fun variables(variables: Map<String, Any?>): Builder

        fun variable(key: String, value: Any?): Builder

        fun locale(locale: Locale): Builder

        fun executionInputCustomizer(
            executionInputCustomizer: GraphQLExecutionInputCustomizer
        ): Builder

        fun executionInputCustomizers(
            executionInputCustomizers: List<GraphQLExecutionInputCustomizer>
        ): Builder

        fun build(): RawGraphQLRequest
    }
}
