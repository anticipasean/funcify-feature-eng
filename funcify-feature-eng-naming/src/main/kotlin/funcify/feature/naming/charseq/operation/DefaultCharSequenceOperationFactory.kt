package funcify.feature.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/19/22
 */
internal object DefaultCharSequenceOperationFactory {

    fun <CS, CSI> createCharSequenceMapOperation(function: (CSI) -> CSI): CharSequenceMapOperation<CS, CSI> {
        return DefaultCharSequenceMapOperation(function)
    }

    fun <CS, CSI> createCharacterGroupFlatteningOperation(function: (CSI) -> CS): CharacterGroupFlatteningOperation<CS, CSI> {
        return DefaultCharacterGroupFlatteningOperation(function)
    }

    fun <CS, CSI> createCharacterGroupingOperation(function: (CS) -> CSI): CharacterGroupingOperation<CS, CSI> {
        return DefaultCharacterGroupingOperation(function)
    }

    fun <CS, CSI> createCharacterMapOperation(function: (CS) -> CS): CharacterMapOperation<CS, CSI> {
        return DefaultCharacterMapOperation(function)
    }


    private class DefaultCharSequenceMapOperation<CS, CSI>(val function: (CSI) -> CSI) : CharSequenceMapOperation<CS, CSI> {
        override fun invoke(input: CSI): CSI {
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

    private class DefaultCharacterMapOperation<CS, CSI>(val function: (CS) -> CS) : CharacterMapOperation<CS, CSI> {
        override fun invoke(input: CS): CS {
            return function.invoke(input)
        }

    }


}