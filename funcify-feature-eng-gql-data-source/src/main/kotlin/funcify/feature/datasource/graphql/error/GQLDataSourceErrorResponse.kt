package funcify.feature.datasource.graphql.error

import arrow.core.Option
import arrow.core.some
import funcify.feature.error.ErrorResponse
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import org.springframework.http.HttpStatus

enum class GQLDataSourceErrorResponse : ErrorResponse {
    
    GRAPHQL_DATA_SOURCE_CREATION_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.BAD_REQUEST.some()
        override val errorMessageIfHttp: Option<String>
            get() = "error occurred during creation of graphql_data_source".some()
    },
    JSON_CONVERSION_ISSUE {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.UNPROCESSABLE_ENTITY.some()
        override val errorMessageIfHttp: Option<String>
            get() = "unable to convert into json".some()
        override val responseIfGraphQL: Option<GraphQLError>
            get() =
                GraphqlErrorBuilder.newError()
                    .errorType(ErrorType.InvalidSyntax)
                    .message("unable to convert into json")
                    .build()
                    .some()
    },
    CLIENT_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.FAILED_DEPENDENCY.some()
        override val errorMessageIfHttp: Option<String>
            get() = "unsuccessful response from graphql client".some()
    },
    MALFORMED_CONTENT_RECEIVED {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.NOT_ACCEPTABLE.some()
        override val errorMessageIfHttp: Option<String>
            get() = "response from graphQL source not in expected format".some()
    },
    INVALID_INPUT {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.NOT_ACCEPTABLE.some()
        override val errorMessageIfHttp: Option<String>
            get() = "invalid input".some()
    },
    UNEXPECTED_ERROR {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "unexpected error".some()
    };

    companion object {
        internal data class GQLSpecificErrorResponse(val gqlError: GraphQLError) : ErrorResponse {
            override val responseIfGraphQL: Option<GraphQLError>
                get() = gqlError.some()
        }
    }
}
