package funcify.feature.materializer.graph

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.execution.ExecutionContext
import graphql.execution.FieldCollector
import graphql.execution.NonNullableFieldValidator
import graphql.normalized.ExecutableNormalizedField
import graphql.normalized.ExecutableNormalizedOperation

internal data class DefaultMaterializationGraphContext(
    override val session: GraphQLSingleRequestSession,
    override val executionContext: ExecutionContext,
    val fieldCollector: FieldCollector,
    val nonNullableFieldValidator: NonNullableFieldValidator,
    val executableNormalizedOperation: ExecutableNormalizedOperation,
    val executableNormalizedField: ExecutableNormalizedField,
) : MaterializationGraphContext
