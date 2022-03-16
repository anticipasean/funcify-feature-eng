package funcify.naming.condition


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface NameCondition : (String) -> Boolean {

    val conditionName: String

    override fun invoke(input: String): Boolean
}