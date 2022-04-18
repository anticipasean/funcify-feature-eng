package funcify.feature.materializer.request

import graphql.ExecutionInput

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLExecutionInputCustomizer :
    (ExecutionInput, ExecutionInput.Builder) -> ExecutionInput {}
