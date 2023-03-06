package funcify.feature.json.data

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 *
 * @author smccarron
 * @created 2023-03-05
 */
internal class KJsonArrayData<WT, I>(val entries: PersistentList<KJsonData<WT, I>>) :
    KJsonContainerData<WT, I> {

    companion object {

        private val EMPTY = KJsonArrayData<Any?, Any?>(persistentListOf<KJsonData<Any?, Any?>>())

        fun <WT, I> empty(): KJsonArrayData<WT, I> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as KJsonArrayData<WT, I>
        }
    }
}
