package funcify.feature.fetcher

import funcify.feature.contract.DataElement
import funcify.feature.contract.DerivedDataElement


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface DataTransformer<C, V> : DataElementFetcher<C, V> {

    override fun getDataElement(): DataElement {
        return getDerivedDataElement()
    }

    fun getDerivedDataElement(): DerivedDataElement

}