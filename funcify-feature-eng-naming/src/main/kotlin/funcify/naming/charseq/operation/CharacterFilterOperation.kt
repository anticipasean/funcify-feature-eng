package funcify.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/19/22
 */
interface CharacterFilterOperation<CS> : (CS) -> Boolean {

    override fun invoke(input: CS): Boolean

}