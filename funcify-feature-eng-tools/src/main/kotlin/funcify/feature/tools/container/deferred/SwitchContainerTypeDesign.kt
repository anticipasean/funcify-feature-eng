package funcify.feature.tools.container.deferred

/**
 * @param SWT
 * - Source Container Witness Type
 * @param TWT
 * - Target Container Witness Type
 * @param I
 * - Container Input Type
 */
internal class SwitchContainerTypeDesign<SWT, TWT, I>(
    val sourceDesign: DeferredDesign<SWT, I>,
    val targetTemplate: DeferredTemplate<TWT>,
    val sourceTemplate: DeferredTemplate<SWT> = sourceDesign.template,
) : DeferredDesign<TWT, I> {

    override val template: DeferredTemplate<TWT> = targetTemplate

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, I> {
        return when (val sourceContainer: DeferredContainer<SWT, I> =
                sourceDesign.fold(sourceTemplate)
        ) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                template.fromKFuture(sourceContainer.kFuture)
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                template.fromMono(sourceContainer.mono)
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                template.fromFlux(sourceContainer.flux)
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${sourceContainer::class.qualifiedName} ]"
                )
            }
        }
    }
}
