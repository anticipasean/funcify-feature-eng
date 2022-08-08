package funcify.feature.materializer.threadlocal

import arrow.core.Option

/**
 *
 * @author smccarron
 * @created 2022-08-04
 */
interface ThreadLocalContextOperationFactory {

    companion object {

        fun defaultFactory(): ThreadLocalContextOperationFactory {
            return DefaultThreadLocalContextOperationFactory()
        }
    }

    fun builder(): ThreadLocalContextInitStep

    interface ThreadLocalContextInitStep {

        fun <O> extractInParentContext(function: () -> Option<O>): ThreadLocalContextExecStep<O>

        fun <S, O> extractInParentContextFrom(
            subject: S,
            function: (S) -> Option<O>
        ): ThreadLocalContextSubjectExecStep<S, O>
    }

    interface ThreadLocalContextExecStep<I> {

        fun setInChildContext(function: (I) -> Unit): ThreadLocalContextOpBuildStep
    }

    interface ThreadLocalContextSubjectExecStep<S, I> {

        fun setInChildContext(function: (S, I) -> Unit): ThreadLocalContextOpBuildStep
    }

    interface ThreadLocalContextOpBuildStep {

        fun build(): ThreadLocalContextOperation
    }
}
