package funcify.feature.tools.string.charseq.factory

import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation
import funcify.feature.tools.string.charseq.input.DefaultStringInputCursor
import funcify.feature.tools.string.charseq.template.CharacterSequenceCursorTemplate


/**
 *
 * @author smccarron
 * @created 3/9/22
 */
interface StandardStringInputCursorFactory<O> : CharacterSequenceCursorTemplate<DefaultStringInputCursor, O> {

    override fun currentRelativeLocation(inputContext: DefaultStringInputCursor): RelativeCharSequenceLocation? {
        return when {
            !inputContext.hasPrevious() && inputContext.hasNext() -> RelativeCharSequenceLocation.FIRST_CHARACTER
            inputContext.hasNext() -> RelativeCharSequenceLocation.MIDDLE_CHARACTER
            inputContext.hasPrevious() -> RelativeCharSequenceLocation.LAST_CHARACTER
            else -> null
        }
    }

    override fun hasCurrentInputChar(inputContext: DefaultStringInputCursor): Boolean {
        return inputContext.hasCurrent()
    }

    override fun currentInputChar(inputContext: DefaultStringInputCursor): Char {
        return inputContext.getCurrentChar()
    }

    override fun hasPreviousCharInput(inputContext: DefaultStringInputCursor): Boolean {
        return inputContext.hasPrevious()
    }

    override fun moveToPreviousCharInput(inputContext: DefaultStringInputCursor): DefaultStringInputCursor {
        return inputContext.apply { moveToPrevious() }
    }

    override fun hasNextInputChar(inputContext: DefaultStringInputCursor): Boolean {
        return inputContext.hasNext()
    }

    override fun moveToNextInputChar(inputContext: DefaultStringInputCursor): DefaultStringInputCursor {
        return inputContext.apply { moveToNext() }
    }
}