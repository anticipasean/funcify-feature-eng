package funcify.feature.fetcher

import funcify.feature.contract.DataElement
import funcify.feature.contract.RawDataElement


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface DataRetriever<C, V> : DataElementFetcher<C, V> {

    override val dataElement: DataElement
        get() = getRawDataElement()

    fun getRawDataElement(): RawDataElement


}