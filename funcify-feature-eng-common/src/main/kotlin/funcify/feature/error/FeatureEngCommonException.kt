package funcify.feature.error

import arrow.core.getOrElse
import arrow.core.or
import arrow.core.toOption
import java.io.PrintStream
import java.io.PrintWriter

open class FeatureEngCommonException(
    val errorResponse: ErrorResponse,
    val inputMessage: String,
    override val cause: Throwable?
) :
    RuntimeException(
        extractMessageGivenInputParameters(errorResponse, inputMessage, cause),
        cause
    ) {

    constructor(
        errorResponse: ErrorResponse
    ) : this(
        errorResponse,
        errorResponse.errorMessageIfGraphQL.or(errorResponse.errorMessageIfHttp).getOrElse {
            MISSING_ERROR_MESSAGE
        },
        null
    )

    constructor(
        errorResponse: ErrorResponse,
        cause: Throwable
    ) : this(
        errorResponse,
        cause
            .message
            .toOption()
            .or(errorResponse.errorMessageIfGraphQL)
            .or(errorResponse.errorMessageIfHttp)
            .getOrElse { MISSING_ERROR_MESSAGE },
        cause
    )

    constructor(
        errorResponse: ErrorResponse,
        inputMessage: String
    ) : this(errorResponse, inputMessage, null)

    constructor(inputMessage: String) : this(DEFAULT_ERROR_RESPONSE, inputMessage, null)

    constructor(
        inputMessage: String,
        cause: Throwable
    ) : this(DEFAULT_ERROR_RESPONSE, inputMessage, cause)

    companion object {

        const val MISSING_ERROR_MESSAGE: String = "no error message supplied"

        val DEFAULT_ERROR_RESPONSE: ErrorResponse = object : ErrorResponse {}

        private fun extractMessageGivenInputParameters(
            errorResponse: ErrorResponse,
            inputMessage: String,
            cause: Throwable?
        ): String {
            return when {
                inputMessage != MISSING_ERROR_MESSAGE -> {
                    inputMessage
                }
                cause != null -> {
                    cause.message.toOption().getOrElse { MISSING_ERROR_MESSAGE }
                }
                errorResponse.errorMessageIfGraphQL.isDefined() -> {
                    errorResponse.errorMessageIfGraphQL.getOrElse { MISSING_ERROR_MESSAGE }
                }
                else -> {
                    errorResponse.errorMessageIfHttp.getOrElse { MISSING_ERROR_MESSAGE }
                }
            }
        }
    }

    override val message: String?
        get() = super.message

    override fun getLocalizedMessage(): String {
        return super.getLocalizedMessage()
    }

    override fun initCause(cause: Throwable?): Throwable {
        return super.initCause(cause)
    }

    override fun printStackTrace() {
        super.printStackTrace()
    }

    override fun printStackTrace(s: PrintStream?) {
        super.printStackTrace(s)
    }

    override fun printStackTrace(s: PrintWriter?) {
        super.printStackTrace(s)
    }

    override fun fillInStackTrace(): Throwable {
        return super.fillInStackTrace()
    }

    override fun getStackTrace(): Array<StackTraceElement> {
        return super.getStackTrace()
    }

    override fun setStackTrace(stackTrace: Array<out StackTraceElement>?) {
        super.setStackTrace(stackTrace)
    }
}
