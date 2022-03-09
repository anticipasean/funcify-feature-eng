package funcify.feature.tools.string.charseq.strategy

import arrow.core.Option
import arrow.core.some
import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation
import funcify.feature.tools.string.charseq.ops.DropNonAlphanumericLastCharacters
import funcify.feature.tools.string.charseq.ops.DropWhitespaceLastCharacters


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
interface MiddleCharacterHandlingStrategy<I> : CharacterHandlingStrategy<I> {

    override val restrictedToLocation: Option<RelativeCharSequenceLocation>
        get() = RelativeCharSequenceLocation.MIDDLE_CHARACTER.some()

    fun dropAnyLastWhitespaceCharacters(): LastCharacterHandlingStrategy<I> {
        return DropWhitespaceLastCharacters<I>()
    }

    fun dropAnyLastNonAlphanumericCharacters(): LastCharacterHandlingStrategy<I> {
        return DropNonAlphanumericLastCharacters<I>()
    }
}