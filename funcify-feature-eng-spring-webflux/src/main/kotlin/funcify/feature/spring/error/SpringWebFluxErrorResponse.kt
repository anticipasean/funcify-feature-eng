package funcify.feature.spring.error

import arrow.core.Option
import arrow.core.some
import funcify.feature.error.ErrorResponse
import org.springframework.http.HttpStatus

enum class SpringWebFluxErrorResponse : ErrorResponse {
    INVALID_INPUT {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.BAD_REQUEST.some()
        override val errorMessageIfHttp: Option<String>
            get() = "invalid input".some()
    },
    NO_RESPONSE_PROVIDED {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "no serialized_graphql_response provided".some()
    },
    TOO_MANY_RESPONSES_PROVIDED {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() =
                "more than one serialized_graphql_response given for single_request_session".some()
    }
}
