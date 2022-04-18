package funcify.feature.materializer.error

import funcify.feature.error.ErrorResponse
import funcify.feature.error.FeatureEngCommonException

class MaterializerException(
    override val errorResponse: ErrorResponse,
    override val inputMessage: String,
    override val cause: Throwable?
) : FeatureEngCommonException(errorResponse, inputMessage, cause) {

    constructor(errorResponse: ErrorResponse) : this(errorResponse, MISSING_ERROR_MESSAGE, null)

    constructor(inputMessage: String) : this(DEFAULT_ERROR_RESPONSE, inputMessage, null)

    constructor(
        inputMessage: String,
        cause: Throwable
    ) : this(DEFAULT_ERROR_RESPONSE, inputMessage, cause)
    
}
