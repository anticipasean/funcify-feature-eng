package funcify.feature.datasource.rest.error

import arrow.core.or
import funcify.feature.error.FeatureEngCommonException

class RestApiDataSourceException(
    errorResponse: RestApiErrorResponse,
    inputMessage: String,
    cause: Throwable?
) : FeatureEngCommonException(errorResponse, inputMessage, cause) {

    constructor(
        errorResponse: RestApiErrorResponse,
        inputMessage: String
    ) : this(errorResponse, inputMessage, null)

    constructor(
        errorResponse: RestApiErrorResponse,
        cause: Throwable
    ) : this(
        errorResponse,
        errorResponse.errorMessageIfHttp.or(errorResponse.errorMessageIfGraphQL).orNull()
            ?: MISSING_ERROR_MESSAGE,
        cause
    )
}
