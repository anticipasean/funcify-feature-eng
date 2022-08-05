package funcify.feature.materializer.context

import arrow.core.Option
import funcify.feature.materializer.context.ThreadLocalContextOperationFactory.ThreadLocalContextExecStep
import funcify.feature.materializer.context.ThreadLocalContextOperationFactory.ThreadLocalContextInitStep
import funcify.feature.materializer.context.ThreadLocalContextOperationFactory.ThreadLocalContextOpBuildStep
import funcify.feature.materializer.context.ThreadLocalContextOperationFactory.ThreadLocalContextSubjectExecStep

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

            override fun setInChildContext(function: (O) -> Unit): ThreadLocalContextOpBuildStep {
                return DefaultThreadLocalContextOpBuildStep<Any, O>(extractor, function)
            }
        }

        private class DefaultThreadLocalContextSubjectExecStep<S, O>(
            private val subject: S,
            private val extractor: (S) -> Option<O>
        ) : ThreadLocalContextSubjectExecStep<S, O> {

            override fun setInChildContext(
                function: (S, O) -> Unit
            ): ThreadLocalContextOpBuildStep {
                return DefaultThreadLocalContextOpBuildStep<S, O>(
                    null,
                    null,
                    subject,
                    extractor,
                    function
                )
            }
        }

        private class DefaultThreadLocalContextOpBuildStep<S, O>(
            private val extractor: (() -> Option<O>)? = null,
            private val setter: ((O) -> Unit)? = null,
            private val subject: S? = null,
            private val extractorWithSubject: ((S) -> Option<O>)? = null,
            private val setterWithSubject: ((S, O) -> Unit)? = null
        ) : ThreadLocalContextOpBuildStep {

            override fun build(): ThreadLocalContextOperation {
                return when (subject) {
                    null -> {
                        DefaultThreadLocalContextOperation<O>(extractor!!, setter!!)
                    }
                    else -> {
                        DefaultThreadLocalContextOperationWithSubject<S, O>(
                            subject,
                            extractorWithSubject!!,
                            setterWithSubject!!
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
