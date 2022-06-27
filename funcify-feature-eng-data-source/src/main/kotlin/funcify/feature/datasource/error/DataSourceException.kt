package funcify.feature.datasource.error

import arrow.core.or
import funcify.feature.error.ErrorResponse
import funcify.feature.error.FeatureEngCommonException

/**
 *
 * @author smccarron
 * @created 2022-06-27
 */
class DataSourceException(errorResponse: ErrorResponse, inputMessage: String, cause: Throwable?) :
    FeatureEngCommonException(errorResponse, inputMessage, cause) {

    constructor(
        errorResponse: ErrorResponse,
        inputMessage: String
    ) : this(errorResponse, inputMessage, null)

    constructor(
        errorResponse: ErrorResponse
    ) : this(
        errorResponse,
        errorResponse.errorMessageIfGraphQL.or(errorResponse.errorMessageIfHttp).orNull()
            ?: MISSING_ERROR_MESSAGE,
        null
    )

    constructor(inputMessage: String) : this(DEFAULT_ERROR_RESPONSE, inputMessage, null)

    constructor(
        errorResponse: ErrorResponse,
        cause: Throwable
    ) : this(
        errorResponse,
        errorResponse.errorMessageIfGraphQL.or(errorResponse.errorMessageIfHttp).orNull()
            ?: MISSING_ERROR_MESSAGE,
        cause
    )
}
