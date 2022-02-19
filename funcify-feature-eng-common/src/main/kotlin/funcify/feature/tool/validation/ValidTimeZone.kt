package funcify.feature.tool.validation


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
@kotlin.annotation.Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.FIELD,
        AnnotationTarget.TYPE,
                         )
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
annotation class ValidTimeZone(val message: String = "The specific time zone is invalid.")
