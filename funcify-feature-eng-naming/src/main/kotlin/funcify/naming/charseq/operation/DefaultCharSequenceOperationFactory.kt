package funcify.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/19/22
 */
object DefaultCharSequenceOperationFactory {

    fun <CSI> createCharSequenceFilterOperation(function: (CSI) -> Boolean): CharSequenceFilterOperation<CSI> {
        return DefaultCharSequenceFilterOperation(function)
    }

    fun <CS> createCharSequenceMapOperation(function: (CS) -> CS): CharSequenceMapOperation<CS> {
        return DefaultCharSequenceMapOperation(function)
    }

    fun <CS> createCharacterFilterOperation(function: (CS) -> Boolean): CharacterFilterOperation<CS> {
        return DefaultCharacterFilterOperation(function)
    }

    fun <CS, CSI> createCharacterGroupFlatteningOperation(function: (CSI) -> CS): CharacterGroupFlatteningOperation<CS, CSI> {
        return DefaultCharacterGroupFlatteningOperation(function)
    }

    fun <CS, CSI> createCharacterGroupingOperation(function: (CS) -> CSI): CharacterGroupingOperation<CS, CSI> {
        return DefaultCharacterGroupingOperation(function)
    }

    fun <CS> createCharacterMapOperation(function: (CS) -> CS): CharacterMapOperation<CS> {
        return DefaultCharacterMapOperation(function)
    }

    private class DefaultCharSequenceFilterOperation<CSI>(val function: (CSI) -> Boolean) : CharSequenceFilterOperation<CSI> {
        override fun invoke(input: CSI): Boolean {
            return function.invoke(input)
        }

    }

    private class DefaultCharSequenceMapOperation<CS>(val function: (CS) -> CS) : CharSequenceMapOperation<CS> {
        override fun invoke(input: CS): CS {
            return function.invoke(input)
        }

    }

    private class DefaultCharacterFilterOperation<CS>(val function: (CS) -> Boolean) : CharacterFilterOperation<CS> {
        override fun invoke(input: CS): Boolean {
            return function.invoke(input)
        }

    }

    private class DefaultCharacterGroupFlatteningOperation<CS, CSI>(val function: (CSI) -> CS) : CharacterGroupFlatteningOperation<CS, CSI> {
        override fun invoke(input: CSI): CS {
            return function.invoke(input)
        }

    }

    private class DefaultCharacterGroupingOperation<CS, CSI>(val function: (CS) -> CSI) : CharacterGroupingOperation<CS, CSI> {
        override fun invoke(input: CS): CSI {
            return function.invoke(input)
        }

    }

    private class DefaultCharacterMapOperation<CS>(val function: (CS) -> CS) : CharacterMapOperation<CS> {
        override fun invoke(input: CS): CS {
            return function.invoke(input)
        }

    }


}