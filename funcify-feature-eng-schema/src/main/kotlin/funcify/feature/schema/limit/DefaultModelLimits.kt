package funcify.feature.schema.limit

import kotlin.math.max

internal data class DefaultModelLimits(
    private val inputMaximumOperationDepth: Int = ModelLimits.DEFAULT_MAXIMUM_OPERATION_DEPTH
) : ModelLimits {

    override val maximumOperationDepth: Int =
        max(ModelLimits.REQUIRED_MINIMUM_OPERATION_DEPTH, inputMaximumOperationDepth)
}
