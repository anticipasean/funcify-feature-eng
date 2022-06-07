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
    },
    INVALID_GRAPHQL_REQUEST {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.BAD_REQUEST.some()
        override val errorMessageIfHttp: Option<String>
            get() = "invalid graphql request".some()
    },
    GRAPHQL_SCHEMA_CREATION_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "error occurred during graphql schema creation".some()
    };
    companion object {

    }
}
