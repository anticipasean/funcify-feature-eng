package funcify.feature.fetcher

import funcify.feature.contract.DataElement
import funcify.feature.contract.DerivedDataElement


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface DataTransformer<C, V> : DataElementFetcher<C, V> {

    override val dataElement: DataElement
        get() = getDerivedDataElement()


    fun getDerivedDataElement(): DerivedDataElement

}