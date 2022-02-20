package funcify.feature.fetcher

import funcify.feature.contract.DataElement


/**
 * A function for fetching a value for a given data element that
 * takes a context parameter (C) and returns a value of type (V)
 * @author smccarron
 * @created 2/8/22
 */
interface DataElementFetcher<C, V> : (C) -> V {

    val dataElement: DataElement

}