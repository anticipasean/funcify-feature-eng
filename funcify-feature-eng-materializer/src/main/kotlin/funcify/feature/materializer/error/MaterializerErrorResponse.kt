package funcify.feature.materializer.error

import arrow.core.Option
import arrow.core.some
import funcify.feature.error.ErrorResponse
import org.springframework.http.HttpStatus

enum class MaterializerErrorResponse : ErrorResponse {
    UNEXPECTED_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "unexpected error occurred during materialization session".some()
    },
    METAMODEL_GRAPH_CREATION_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "error occurred when creating startup metamodel graph".some()
    };
    companion object {}
}
