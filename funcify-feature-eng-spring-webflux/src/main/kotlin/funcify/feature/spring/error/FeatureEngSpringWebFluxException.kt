package funcify.feature.spring.error

import arrow.core.or
import funcify.feature.error.ErrorResponse
import funcify.feature.error.FeatureEngCommonException

class FeatureEngSpringWebFluxException(
    errorResponse: ErrorResponse,
    inputMessage: String,
    cause: Throwable?
) : FeatureEngCommonException(errorResponse, inputMessage, cause) {

    constructor(
        errorResponse: ErrorResponse
    ) : this(
        errorResponse,
        errorResponse.errorMessageIfGraphQL.or(errorResponse.errorMessageIfHttp).orNull()
            ?: MISSING_ERROR_MESSAGE,
        null
    )

    constructor(
        errorResponse: ErrorResponse,
        cause: Throwable
    ) : this(
        errorResponse,
        errorResponse.errorMessageIfGraphQL.or(errorResponse.errorMessageIfHttp).orNull()
            ?: MISSING_ERROR_MESSAGE,
        cause
    )

    constructor(
        inputMessage: String,
        cause: Throwable
    ) : this(DEFAULT_ERROR_RESPONSE, inputMessage, cause)

    constructor(
        errorResponse: ErrorResponse,
        inputMessage: String
    ) : this(errorResponse, inputMessage, null)
}
