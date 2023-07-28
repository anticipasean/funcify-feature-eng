package funcify.feature.materializer.request

import arrow.core.foldLeft
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import graphql.execution.ExecutionId
import java.net.URI
import java.security.Principal
import java.util.*
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.springframework.messaging.MessageHeaders
import reactor.core.publisher.Mono

internal data class DefaultRawGraphQLRequest(
    override val requestId: UUID,
    override val executionId: ExecutionId,
    override val uri: URI,
    override val headers: MessageHeaders,
    override val principalPublisher: Mono<out Principal> = Mono.empty(),
    override val rawGraphQLQueryText: String = "",
    override val operationName: String = "",
    override val variables: PersistentMap<String, Any?> = persistentMapOf(),
    override val locale: Locale = Locale.getDefault(),
    override val expectedOutputFieldNames: PersistentList<String> = persistentListOf(),
    override val executionInputCustomizers: PersistentList<GraphQLExecutionInputCustomizer> =
        persistentListOf(),
) : RawGraphQLRequest {

    private val stringForm: String by lazy {
        mapOf<String, Any?>(
                "requestId" to requestId,
                "executionId" to executionId,
                "uri" to uri,
                "headers" to headers,
                "principalPublisher" to principalPublisher,
                "rawGraphQLQueryText" to rawGraphQLQueryText,
                "operationName" to operationName,
                "variables" to variables,
                "locale" to locale,
                "expectedOutputFieldNames" to expectedOutputFieldNames,
                "executionInputCustomizers" to executionInputCustomizers,
            )
            .foldLeft(JsonNodeFactory.instance.objectNode()) { on: ObjectNode, (k: String, v: Any?)
                ->
                on.put(k, Objects.toString(v, "<NA>"))
            }
            .toString()
    }

    override fun toString(): String {
        return stringForm
    }
}
