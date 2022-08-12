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
    },
    STRATEGY_INCORRECTLY_APPLIED {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "a strategy has been incorrectly applied to input".some()
    },
    STRATEGY_MISSING {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "no strategies given for this ".some()
    },
    MISSING_PARAMETER {
        override val responseStatusIfHttp: Option<HttpStatus>
            get() = HttpStatus.INTERNAL_SERVER_ERROR.some()
        override val errorMessageIfHttp: Option<String>
            get() = "crucial parameter value has not been set on builder".some()
    }
}
