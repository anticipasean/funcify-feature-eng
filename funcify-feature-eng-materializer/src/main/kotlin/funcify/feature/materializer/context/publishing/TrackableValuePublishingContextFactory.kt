package funcify.feature.materializer.context.publishing

/**
 *
 * @author smccarron
 * @created 2022-12-02
 */
interface TrackableValuePublishingContextFactory {

    fun builder(): TrackableValuePublishingContext.Builder

}
