package funcify.feature.type

import arrow.core.Option
import arrow.core.toOption


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
object NumericTypeExtensions {

    fun Int?.asPositiveInt(): Option<PositiveInt> {
        return this.toOption()
                .filter { i -> i >= 0 }
                .map(::PositiveInt)
    }

    fun Int?.ifPositive(): Option<Int> {
        return this.toOption()
                .filter { i -> i >= 0 }
    }

    fun Double?.asPositiveDouble(): Option<PositiveDouble> {
        return this.toOption()
                .filter { d -> d >= 0.0 }
                .map(::PositiveDouble)
    }

    fun Double?.ifPositive(): Option<Double> {
        return this.toOption()
                .filter { d -> d >= 0.0 }
    }

}