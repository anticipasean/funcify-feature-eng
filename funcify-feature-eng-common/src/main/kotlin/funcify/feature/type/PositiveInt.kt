package funcify.feature.type

import com.fasterxml.jackson.annotation.JsonValue

data class PositiveInt(@JsonValue
                       private val intValue: Int) : Number() {

    init {
        if (intValue < 0) {
            throw IllegalArgumentException("input int value [ $intValue ] is not positive")
        }
    }

    override fun toByte(): Byte {
        return intValue.toByte()
    }

    override fun toChar(): Char {
        return intValue.toChar()
    }

    override fun toDouble(): Double {
        return intValue.toDouble()
    }

    override fun toFloat(): Float {
        return intValue.toFloat()
    }

    override fun toInt(): Int {
        return intValue
    }

    override fun toLong(): Long {
        return intValue.toLong()
    }

    override fun toShort(): Short {
        return intValue.toShort()
    }


}