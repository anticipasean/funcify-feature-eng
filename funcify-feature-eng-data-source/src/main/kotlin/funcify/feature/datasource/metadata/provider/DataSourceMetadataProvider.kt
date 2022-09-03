package funcify.feature.datasource.metadata.provider

import reactor.core.publisher.Mono

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

    fun provideMetadata(service: S): Mono<MD>
}
