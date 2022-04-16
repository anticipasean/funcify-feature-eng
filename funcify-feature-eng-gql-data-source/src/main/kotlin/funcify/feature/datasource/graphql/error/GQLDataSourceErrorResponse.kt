package funcify.feature.datasource.graphql.error

import arrow.core.Option
import arrow.core.some
import funcify.feature.error.ErrorResponse
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import org.springframework.http.HttpStatus

enum class GQLDataSourceErrorResponse : ErrorResponse {
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
    }
}
