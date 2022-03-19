package funcify.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharacterMapOperation<CS>: (CS) -> CS {

    override fun invoke(input: CS): CS
}