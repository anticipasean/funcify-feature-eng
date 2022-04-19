package funcify.feature.schema.error

import funcify.feature.error.ErrorResponse
import funcify.feature.error.FeatureEngCommonException

class SchemaException(errorResponse: ErrorResponse, inputMessage: String, cause: Throwable?) :
    FeatureEngCommonException(errorResponse, inputMessage, cause) {

    constructor(
        errorResponse: ErrorResponse,
        inputMessage: String
    ) : this(errorResponse, inputMessage, null)

    constructor(errorResponse: ErrorResponse) : this(errorResponse, MISSING_ERROR_MESSAGE, null)

    constructor(inputMessage: String) : this(DEFAULT_ERROR_RESPONSE, inputMessage, null)

    constructor(
        inputMessage: String,
        cause: Throwable
    ) : this(DEFAULT_ERROR_RESPONSE, inputMessage, cause)
}
