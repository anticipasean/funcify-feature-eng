package funcify.feature.datasource.metadata.provider

import funcify.feature.tools.container.async.KFuture

/**
 *
 * @param S
 * - service type that can provide metadata
 * @param MD
 * - metadata carrying type
 * @author smccarron
 * @created 4/4/22
 */
fun interface DataSourceMetadataProvider<S, MD> {

    fun provideMetadata(service: S): KFuture<MD>
}
