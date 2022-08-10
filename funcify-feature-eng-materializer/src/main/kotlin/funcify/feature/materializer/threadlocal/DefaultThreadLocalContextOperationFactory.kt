package funcify.feature.materializer.threadlocal

import arrow.core.Option
import funcify.feature.materializer.threadlocal.ThreadLocalContextOperationFactory.ThreadLocalContextExecStep
import funcify.feature.materializer.threadlocal.ThreadLocalContextOperationFactory.ThreadLocalContextInitStep
import funcify.feature.materializer.threadlocal.ThreadLocalContextOperationFactory.ThreadLocalContextOpBuildStep
import funcify.feature.materializer.threadlocal.ThreadLocalContextOperationFactory.ThreadLocalContextResetStep
import funcify.feature.materializer.threadlocal.ThreadLocalContextOperationFactory.ThreadLocalContextSubjectExecStep
import funcify.feature.materializer.threadlocal.ThreadLocalContextOperationFactory.ThreadLocalContextSubjectResetStep

internal class DefaultThreadLocalContextOperationFactory : ThreadLocalContextOperationFactory {

    companion object {

        private class DefaultThreadLocalContextInitStep : ThreadLocalContextInitStep {

            override fun <O> extractInParentContext(
                function: () -> Option<O>
            ): ThreadLocalContextExecStep<O> {
                return DefaultThreadLocalContextExecStep(function)
            }

            override fun <S, O> extractInParentContextFrom(
                subject: S,
                function: (S) -> Option<O>
            ): ThreadLocalContextSubjectExecStep<S, O> {
                return DefaultThreadLocalContextSubjectExecStep(subject, function)
            }
        }

        private class DefaultThreadLocalContextExecStep<O>(private val extractor: () -> Option<O>) :
            ThreadLocalContextExecStep<O> {

            override fun setInChildContext(function: (O) -> Unit): ThreadLocalContextResetStep<O> {
                return DefaultThreadLocalContextResetStep<O>(extractor, function)
            }
        }

        private class DefaultThreadLocalContextSubjectExecStep<S, O>(
            private val subject: S,
            private val extractor: (S) -> Option<O>
        ) : ThreadLocalContextSubjectExecStep<S, O> {

            override fun setInChildContext(
                function: (S, O) -> Unit
            ): ThreadLocalContextSubjectResetStep<S, O> {
                return DefaultThreadLocalContextSubjectResetStep<S, O>(subject, extractor, function)
            }
        }

        private class DefaultThreadLocalContextResetStep<I>(
            private val extractor: () -> Option<I>,
            private val setter: (I) -> Unit
        ) : ThreadLocalContextResetStep<I> {

            override fun unsetInChildContext(function: (I) -> Unit): ThreadLocalContextOpBuildStep {
                return DefaultThreadLocalContextOpBuildStep<Any, I>(
                    extractor,
                    setter,
                    function,
                    null,
                    null,
                    null,
                    null
                )
            }
        }

        private class DefaultThreadLocalContextSubjectResetStep<S, I>(
            private val subject: S,
            private val extractor: (S) -> Option<I>,
            private val setter: (S, I) -> Unit
        ) : ThreadLocalContextSubjectResetStep<S, I> {

            override fun unsetInChildContext(
                function: (S, I) -> Unit
            ): ThreadLocalContextOpBuildStep {
                return DefaultThreadLocalContextOpBuildStep<S, I>(
                    null,
                    null,
                    null,
                    subject,
                    extractor,
                    setter,
                    function
                )
            }
        }

        private class DefaultThreadLocalContextOpBuildStep<S, O>(
            private val extractor: (() -> Option<O>)? = null,
            private val setter: ((O) -> Unit)? = null,
            private val unsetter: ((O) -> Unit)? = null,
            private val subject: S? = null,
            private val extractorWithSubject: ((S) -> Option<O>)? = null,
            private val setterWithSubject: ((S, O) -> Unit)? = null,
            private val unsetterWithSubject: ((S, O) -> Unit)? = null
        ) : ThreadLocalContextOpBuildStep {

            override fun build(): ThreadLocalContextOperation {
                return when (subject) {
                    null -> {
                        DefaultThreadLocalContextOperation<O>(extractor!!, setter!!, unsetter!!)
                    }
                    else -> {
                        DefaultThreadLocalContextOperationWithSubject<S, O>(
                            subject,
                            extractorWithSubject!!,
                            setterWithSubject!!,
                            unsetterWithSubject!!
                        )
                    }
                }
            }
        }
    }

    override fun builder(): ThreadLocalContextInitStep {
        return DefaultThreadLocalContextInitStep()
    }
}
