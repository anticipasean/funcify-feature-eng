package funcify.feature.error

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import graphql.ErrorClassification
import graphql.GraphQLError
import org.springframework.http.HttpStatus

/**
 * Instances of this type are to be used in providing error feedback to the caller of the service
 * whether the call was made through a REST API, a GraphQL API, or other type of API
 */
interface ErrorResponse {

    /**
     * Indicates what the caller of the API should receive as a response code per an HTTP response
     * specification
     * ```
     * get() => Some<HttpStatus>(HttpStatus.INTERNAL_SERVER_ERROR)
     * ```
     * if not otherwise specified
     */
    val responseStatusIfHttp: Option<HttpStatus>
        get() = HttpStatus.INTERNAL_SERVER_ERROR.some()

    /**
     * Indicates the error message per an HTTP response specification
     * ```
     * get() => Some<String>("Internal Server Error")
     * ```
     * if not otherwise specified
     */
    val errorMessageIfHttp: Option<String>
        get() = responseStatusIfHttp.map { hs: HttpStatus -> hs.reasonPhrase }

    /**
     * Indicates what the caller of the API should receive as a response per a GraphQL specification
     * ```
     * get() => None<GraphQLError>()
     * ```
     * if not otherwise specified
     */
    val responseIfGraphQL: Option<GraphQLError>
        get() = None

    /**
     * Indicates the error classification per a GraphQL specification if a [GraphQLError] was raised
     */
    val errorClassificationIfGraphQL: Option<ErrorClassification>
        get() = responseIfGraphQL.map { gqlError: GraphQLError -> gqlError.errorType }

    /** Indicates the error message per a GraphQL specification if a [GraphQLError] was raised */
    val errorMessageIfGraphQL: Option<String>
        get() = responseIfGraphQL.map { gqlError: GraphQLError -> gqlError.message }
}
