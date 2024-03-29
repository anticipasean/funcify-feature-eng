package funcify.feature.tools.container.async

import arrow.core.None
import arrow.core.Option
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor

/**
 *
 * @author smccarron
 * @created 4/6/22
 */
object KFutureFactory {

    data class WrappedCompletionStageAndExecutor<T>(
        val completionStage: CompletionStage<out T>,
        val executorOpt: Option<Executor> = None
    ) : KFuture<T> {

        override fun <R> fold(
            stageAndExecutor: (CompletionStage<out T>, Option<Executor>) -> R
        ): R {
            return stageAndExecutor.invoke(completionStage, executorOpt)
        }
    }
}
