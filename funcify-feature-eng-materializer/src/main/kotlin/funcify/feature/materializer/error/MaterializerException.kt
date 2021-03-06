package funcify.feature.materializer.error

import arrow.core.or
import funcify.feature.error.FeatureEngCommonException
import funcify.feature.materializer.error.MaterializerErrorResponse.UNEXPECTED_ERROR

class MaterializerException(
    override val errorResponse: MaterializerErrorResponse,
    override val inputMessage: String,
    override val cause: Throwable?
) : FeatureEngCommonException(errorResponse, inputMessage, cause) {

    constructor(
        errorResponse: MaterializerErrorResponse,
        inputMessage: String
    ) : this(errorResponse, inputMessage, null)

    constructor(
        errorResponse: MaterializerErrorResponse,
        cause: Throwable
    ) : this(
        errorResponse,
        errorResponse.errorMessageIfHttp.orNull() ?: MISSING_ERROR_MESSAGE,
        cause
    )

    constructor(inputMessage: String) : this(UNEXPECTED_ERROR, inputMessage, null)

    constructor(
        inputMessage: String,
        cause: Throwable
    ) : this(UNEXPECTED_ERROR, inputMessage, cause)

    constructor(
        errorResponse: MaterializerErrorResponse
    ) : this(
        errorResponse,
        errorResponse.errorMessageIfHttp.or(errorResponse.errorMessageIfGraphQL).orNull()
            ?: MISSING_ERROR_MESSAGE,
        null
    )
}
