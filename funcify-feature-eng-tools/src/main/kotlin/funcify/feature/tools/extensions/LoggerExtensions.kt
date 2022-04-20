package funcify.feature.tools.extensions

import kotlin.reflect.typeOf
import org.slf4j.LoggerFactory

object LoggerExtensions {

    inline fun <reified C> loggerFor(): org.slf4j.Logger {
        return LoggerFactory.getLogger(typeOf<C>()::class.java)
    }

}
