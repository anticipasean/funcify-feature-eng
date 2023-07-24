package funcify.feature.materializer.request

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
) : RawGraphQLRequest {}
