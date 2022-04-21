package funcify.feature.tools.extensions

import org.slf4j.LoggerFactory

object LoggerExtensions {

    inline fun <reified C> loggerFor(): org.slf4j.Logger {
        return LoggerFactory.getLogger(C::class.java)
    }
}
