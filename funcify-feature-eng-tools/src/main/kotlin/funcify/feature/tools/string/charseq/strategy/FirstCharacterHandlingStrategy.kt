package funcify.feature.tools.string.charseq.strategy

import arrow.core.Option
import arrow.core.some
import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
interface FirstCharacterHandlingStrategy<I> : CharacterHandlingStrategy<I> {

    override val restrictedToLocation: Option<RelativeCharSequenceLocation>
        get() = RelativeCharSequenceLocation.FIRST_CHARACTER.some()


}