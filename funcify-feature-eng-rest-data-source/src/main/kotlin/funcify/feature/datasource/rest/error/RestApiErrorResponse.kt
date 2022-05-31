package funcify.feature.datasource.rest.error

import arrow.core.Option
import arrow.core.some
import funcify.feature.error.ErrorResponse
import org.springframework.http.HttpStatus

enum class RestApiErrorResponse : ErrorResponse {

    UNEXPECTED_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "unexpected error occurred".some()
    }
}
