package funcify.feature.data

import funcify.feature.tools.container.async.Async


/**
 *
 * @author smccarron
 * @created 4/4/22
 */
interface DataFetcherMetadataProvider<T> {

    fun provideMetadata(): Async<T>

}