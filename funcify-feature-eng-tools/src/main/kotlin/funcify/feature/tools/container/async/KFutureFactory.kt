package funcify.feature.tools.container.async

import arrow.core.None
import arrow.core.Option
import funcify.feature.tools.container.attempt.KFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor


/**
 *
 * @author smccarron
 * @created 4/6/22
 */
object KFutureFactory {

    internal data class WrappedCompletionStageAndExecutor<T>(private val completionStage: CompletionStage<out T>,
                                                             private val executorOpt: Option<Executor> = None) : KFuture<T> {

        override fun <R> fold(stageAndExecutor: (CompletionStage<out T>, Option<Executor>) -> R): R {
            return stageAndExecutor.invoke(completionStage,
                                           executorOpt)
        }

    }

}