package funcify.feature.tools.container.deferred.design

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.template.DeferredTemplate
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import java.util.concurrent.Executor
import reactor.core.scheduler.Scheduler

internal class ZipDesign<SWT, I, I1, I2, I3, O>(
    override val template: DeferredTemplate<SWT>,
    val currentDesign: DeferredDesign<SWT, I>,
    val other1: DeferredDesign<SWT, I1>,
    val other2: DeferredDesign<SWT, I2>? = null,
    val other3: DeferredDesign<SWT, I3>? = null,
    val combiner1: ((I, I1) -> O)? = null,
    val combiner2: ((I, I1, I2) -> O)? = null,
    val combiner3: ((I, I1, I2, I3) -> O)? = null,
    val execOpt: Option<Either<Executor, Scheduler>> = None
) : DeferredDesign<SWT, O> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, O> {
        return when {
            combiner1 != null -> {
                template.zip1(other1.fold(template), combiner1, currentDesign.fold(template))
            }
            other2 != null && combiner2 != null -> {
                template.zip2(
                    other1.fold(template),
                    other2.fold(template),
                    combiner2,
                    currentDesign.fold(template)
                )
            }
            other2 != null && other3 != null && combiner3 != null -> {
                template.zip3(
                    other1.fold(template),
                    other2.fold(template),
                    other3.fold(template),
                    combiner3,
                    currentDesign.fold(template)
                )
            }
            else -> {
                throw IllegalArgumentException(
                    """one or more parameters necessary for zip method invocation is null 
                        |and thus cannot be invoked 
                        |[ other2: ${other2}, other3: ${other3}, 
                        |combiner1: ${combiner1}, combiner2: ${combiner2}, 
                        |combiner3: ${combiner3} ]""".flattenIntoOneLine()
                )
            }
        }
    }
}
