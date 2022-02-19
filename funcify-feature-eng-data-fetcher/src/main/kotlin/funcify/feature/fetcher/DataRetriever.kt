package funcify.feature.fetcher

import funcify.feature.contract.DataElement
import funcify.feature.contract.RawDataElement


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface DataRetriever<C, V> : DataElementFetcher<C, V> {

    override fun getDataElement(): DataElement {
        return getRawDataElement()
    }

    fun getRawDataElement(): RawDataElement


}