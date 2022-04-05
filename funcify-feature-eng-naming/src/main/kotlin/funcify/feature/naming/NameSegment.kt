package funcify.feature.naming


/**
 * Component of a qualified or conventional name type
 * @author smccarron
 * @created 3/16/22
 */
interface NameSegment {

    /**
     * Value that does not contain any syntax characters: a delimiter e.g. ' ' or '_'
     * or any others specified in the implementation as syntactical
     */
    val value: String

    /**
     * @implementation_note: Should override with value output
     * but this can't be done on interface types
     */
    override fun toString(): String

}