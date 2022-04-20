package funcify.feature.schema.error

import arrow.core.Option
import arrow.core.some
import funcify.feature.error.ErrorResponse
import org.springframework.http.HttpStatus

enum class SchemaErrorResponse : ErrorResponse {
    UNEXPECTED_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "unexpected error".some()
    },
    INVALID_INPUT {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.NOT_ACCEPTABLE.some()
        override val errorMessageIfHttp: Option<String>
            get() = "invalid input passed to function".some()
    }
}
