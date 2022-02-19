package funcify.feature.type

import com.fasterxml.jackson.annotation.JsonValue


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
data class PositiveDouble(@JsonValue
                          private val doubleValue: Double) : Number() {

    init {
        if (doubleValue < 0) {
            throw IllegalArgumentException("input double value [ $doubleValue ] is not positive")
        }
    }

    override fun toByte(): Byte {
        return doubleValue.toInt()
                .toByte()
    }

    override fun toChar(): Char {
        return doubleValue.toInt()
                .toChar()
    }

    override fun toDouble(): Double {
        return doubleValue
    }

    override fun toFloat(): Float {
        return doubleValue.toFloat()
    }

    override fun toInt(): Int {
        return doubleValue.toInt()
    }

    override fun toLong(): Long {
        return doubleValue.toLong()
    }

    override fun toShort(): Short {
        return doubleValue.toInt()
                .toShort()
    }

}