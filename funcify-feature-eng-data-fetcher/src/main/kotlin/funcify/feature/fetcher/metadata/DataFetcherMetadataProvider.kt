package funcify.feature.fetcher.metadata

import funcify.feature.tools.container.deferred.Deferred

/**
 *
 * @param S
 * - service type that can provide metadata
 * @param MD
 * - metadata carrying type
 * @author smccarron
 * @created 4/4/22
 */
interface DataFetcherMetadataProvider<S, MD> {

    fun provideMetadata(service: S): Deferred<MD>

}
