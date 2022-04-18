package funcify.feature.datasource.graphql.error

import funcify.feature.error.ErrorResponse
import funcify.feature.error.FeatureEngCommonException

class GQLDataSourceException(
    errorResponse: ErrorResponse,
    inputMessage: String,
    cause: Throwable? = null
) : FeatureEngCommonException(errorResponse, inputMessage, cause) {

    constructor(
        errorResponse: ErrorResponse,
        cause: Throwable
    ) : this(
        errorResponse = errorResponse,
        inputMessage = MISSING_ERROR_MESSAGE,
        cause = cause
    )

    constructor(
        errorResponse: ErrorResponse,
        inputMessage: String
    ) : this(errorResponse = errorResponse, inputMessage = inputMessage, cause = null)
}
