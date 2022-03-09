package funcify.feature.tools.string.charseq.strategy

import arrow.core.None
import arrow.core.Option
import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation
import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation.FIRST_CHARACTER
import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation.LAST_CHARACTER
import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation.MIDDLE_CHARACTER
import funcify.feature.tools.string.charseq.ops.AppendCharacter
import funcify.feature.tools.string.charseq.ops.DropNonAlphanumericFirstCharacters
import funcify.feature.tools.string.charseq.ops.FirstCharacterToUpper
import funcify.feature.tools.string.charseq.ops.PreserveCharacter
import funcify.feature.tools.string.charseq.ops.ReplaceCharacter
import funcify.feature.tools.string.charseq.ops.ReplaceCharacterSet
import funcify.feature.tools.string.charseq.template.CharacterSequenceCursorTemplate
import kotlinx.collections.immutable.ImmutableSet


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
interface CharacterHandlingStrategy<I> {

    val restrictedToLocation: Option<RelativeCharSequenceLocation>
        get() = None

    val characterHandlingStrategyContext: CharacterHandlingStrategyContext<I>

    fun dropNonAlphabeticFirstCharacters(): FirstCharacterHandlingStrategy<I> {
        return DropNonAlphanumericFirstCharacters<I>()
    }

    fun firstCharacterToUppercase(): FirstCharacterHandlingStrategy<I> {
        return FirstCharacterToUpper<I>()
    }

    fun firstCharacterToLowercase(): FirstCharacterHandlingStrategy<I> {
        return FirstCharacterToUpper<I>()
    }

    fun appendToEach(character: Char,
                     characterToAppend: Char): MiddleCharacterHandlingStrategy<I> {
        return AppendCharacter<I>(character,
                                  characterToAppend)
    }

    fun replaceAllWith(character: Char,
                       replacementChar: Char): MiddleCharacterHandlingStrategy<I> {
        return ReplaceCharacter<I>(character,
                                   replacementChar)
    }

    fun replaceAnyInSetWith(characterSet: ImmutableSet<Char>,
                            replacementChar: Char): MiddleCharacterHandlingStrategy<I> {
        return ReplaceCharacterSet<I>(characterSet,
                                      replacementChar)
    }

    fun preserveFirstAndMiddle(): MiddleCharacterHandlingStrategy<I> {
        return PreserveCharacter<I>()
    }

    fun <O> fold(cursorTemplate: CharacterSequenceCursorTemplate<I, O>,
                 inputContext: I,
                 outputContext: O): O {
        // Only update context pair initially if started on first character in sequence, otherwise depend on cursor iteration
        var updatedContextPair = when (cursorTemplate.currentRelativeLocation(inputContext)) {
            FIRST_CHARACTER -> {
                characterHandlingStrategyContext.firstCharacterStrategies.fold(characterHandlingStrategyContext.anyCharacterStrategies.fold(inputContext to outputContext,
                                                                                                                                            { acc: Pair<I, O>, strategy: CharacterHandlingStrategy<I> ->
                                                                                                                                                acc.first to strategy.fold(cursorTemplate,
                                                                                                                                                                           acc.first,
                                                                                                                                                                           acc.second)
                                                                                                                                            }),
                                                                               { acc: Pair<I, O>, strategy: CharacterHandlingStrategy<I> ->
                                                                                   acc.first to strategy.fold(cursorTemplate,
                                                                                                              acc.first,
                                                                                                              acc.second)
                                                                               })

            }
            else -> {
                inputContext to outputContext
            }
        }
        while (cursorTemplate.hasNext(updatedContextPair.first)) {
            updatedContextPair = cursorTemplate.moveToNext(updatedContextPair.first) to updatedContextPair.second
            updatedContextPair = when (cursorTemplate.currentRelativeLocation(updatedContextPair.first)) {
                FIRST_CHARACTER -> {
                    characterHandlingStrategyContext.firstCharacterStrategies.fold(characterHandlingStrategyContext.anyCharacterStrategies.fold(updatedContextPair,
                                                                                                                                                { acc: Pair<I, O>, strategy: CharacterHandlingStrategy<I> ->
                                                                                                                                                    acc.first to strategy.fold(cursorTemplate,
                                                                                                                                                                               acc.first,
                                                                                                                                                                               acc.second)
                                                                                                                                                }),
                                                                                   { acc: Pair<I, O>, strategy: CharacterHandlingStrategy<I> ->
                                                                                       acc.first to strategy.fold(cursorTemplate,
                                                                                                                  acc.first,
                                                                                                                  acc.second)
                                                                                   })
                }
                MIDDLE_CHARACTER -> {
                    characterHandlingStrategyContext.middleCharacterStrategies.fold(characterHandlingStrategyContext.anyCharacterStrategies.fold(updatedContextPair,
                                                                                                                                                 { acc: Pair<I, O>, strategy: CharacterHandlingStrategy<I> ->
                                                                                                                                                     acc.first to strategy.fold(cursorTemplate,
                                                                                                                                                                                acc.first,
                                                                                                                                                                                acc.second)
                                                                                                                                                 }),
                                                                                    { acc: Pair<I, O>, strategy: CharacterHandlingStrategy<I> ->
                                                                                        acc.first to strategy.fold(cursorTemplate,
                                                                                                                   acc.first,
                                                                                                                   acc.second)
                                                                                    })
                }
                LAST_CHARACTER -> {
                    characterHandlingStrategyContext.lastCharacterStrategies.fold(characterHandlingStrategyContext.anyCharacterStrategies.fold(updatedContextPair,
                                                                                                                                               { acc: Pair<I, O>, strategy: CharacterHandlingStrategy<I> ->
                                                                                                                                                   acc.first to strategy.fold(cursorTemplate,
                                                                                                                                                                              acc.first,
                                                                                                                                                                              acc.second)
                                                                                                                                               }),
                                                                                  { acc: Pair<I, O>, strategy: CharacterHandlingStrategy<I> ->
                                                                                      acc.first to strategy.fold(cursorTemplate,
                                                                                                                 acc.first,
                                                                                                                 acc.second)
                                                                                  })
                }
            }
        }
        return updatedContextPair.second
    }

}