package funcify.feature.tools.container.deferred.design

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.template.DeferredTemplate
import funcify.feature.tools.container.deferred.template.ExecutionContextDeferredTemplate
import java.util.concurrent.Executor
import reactor.core.publisher.Flux
import reactor.core.scheduler.Scheduler

internal class FlatMapFluxDesign<SWT, I, O>(
    override val template: DeferredTemplate<SWT>,
    val currentDesign: DeferredDesign<SWT, I>,
    val mapper: (I) -> Flux<out O>,
    val execOpt: Option<Either<Executor, Scheduler>> = None
) : DeferredDesign<SWT, O> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, O> {
        return when {
            execOpt.isDefined() && template is ExecutionContextDeferredTemplate -> {
                when (val either = execOpt.orNull()!!) {
                    is Either.Left ->
                        template.flatMapFlux(either.value, mapper, currentDesign.fold(template))
                    is Either.Right ->
                        template.flatMapFlux(either.value, mapper, currentDesign.fold(template))
                }
            }
            else -> {
                template.flatMapFlux(mapper, currentDesign.fold(template))
            }
        }
    }
}
