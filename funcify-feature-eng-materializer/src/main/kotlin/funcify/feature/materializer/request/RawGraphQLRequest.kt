package funcify.feature.materializer.request

import graphql.execution.ExecutionId
import java.net.URI
import java.security.Principal
import java.util.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.springframework.http.HttpHeaders
import org.springframework.messaging.MessageHeaders
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface RawGraphQLRequest {

    val requestId: UUID

    val executionId: ExecutionId

    val uri: URI

    val headers: MessageHeaders

    val principalPublisher: Mono<out Principal>

    val rawGraphQLQueryText: String

    val operationName: String

    val variables: ImmutableMap<String, Any?>

    val locale: Locale

    val expectedOutputFieldNames: ImmutableList<String>

    val executionInputCustomizers: ImmutableList<GraphQLExecutionInputCustomizer>

    interface Builder {

        fun requestId(requestId: UUID): Builder

        fun executionId(executionId: ExecutionId): Builder

        fun uri(uri: URI): Builder

        fun headers(headers: MessageHeaders): Builder

        fun principalPublisher(principalPublisher: Mono<out Principal>): Builder

        fun rawGraphQLQueryText(rawGraphQLQueryText: String): Builder

        fun operationName(operationName: String): Builder

        fun variables(variables: Map<String, Any?>): Builder

        fun variable(key: String, value: Any?): Builder

        fun locale(locale: Locale): Builder

        fun expectedOutputFieldNames(expectedOutputFieldNames: List<String>): Builder

        fun expectedOutputFieldName(expectedOutputFieldName: String): Builder

        fun executionInputCustomizer(
            executionInputCustomizer: GraphQLExecutionInputCustomizer
        ): Builder

        fun executionInputCustomizers(
            executionInputCustomizers: List<GraphQLExecutionInputCustomizer>
        ): Builder

        fun build(): RawGraphQLRequest
    }
}
