package funcify.feature.datasource.error

import arrow.core.Option
import arrow.core.some
import funcify.feature.error.ErrorResponse
import org.springframework.http.HttpStatus

/**
 *
 * @author smccarron
 * @created 2022-06-27
 */
enum class DataSourceErrorResponse : ErrorResponse {

    DATASOURCE_SCHEMA_INTEGRITY_VIOLATION {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "an aspect of schema provided by a datasource violates constraint".some()
    }
}
