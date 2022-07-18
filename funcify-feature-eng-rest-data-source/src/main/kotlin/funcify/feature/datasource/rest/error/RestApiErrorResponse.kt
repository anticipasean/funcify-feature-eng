package funcify.feature.datasource.rest.error

import arrow.core.Option
import arrow.core.some
import funcify.feature.error.ErrorResponse
import org.springframework.http.HttpStatus

enum class RestApiErrorResponse : ErrorResponse {
    REST_API_DATA_SOURCE_CREATION_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.BAD_REQUEST.some()
        override val errorMessageIfHttp: Option<String>
            get() = "error occurred during creation of rest_api_data_source".some()
    },
    UNEXPECTED_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "unexpected error occurred".some()
    },
    INVALID_INPUT {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.BAD_REQUEST.some()
        override val errorMessageIfHttp: Option<String>
            get() = "invalid input provided to method".some()
    }
}
