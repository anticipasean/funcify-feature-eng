package funcify.feature.tool.validation

import java.util.*
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
class TimeZoneInputValidator : ConstraintValidator<ValidTimeZone, String> {


    override fun initialize(constraintAnnotation: ValidTimeZone) {
    }

    override fun isValid(timeZone: String, context: ConstraintValidatorContext): Boolean {
        return TimeZone.getAvailableIDs()
                .asSequence()
                .any(timeZone::equals)
    }

}
