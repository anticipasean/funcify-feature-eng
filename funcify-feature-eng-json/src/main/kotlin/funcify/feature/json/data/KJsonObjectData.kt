package funcify.feature.json.data

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2023-03-05
 */
internal class KJsonObjectData<WT, I>(val entries: PersistentMap<String, KJsonData<WT, I>>) :
    KJsonContainerData<WT, I> {

    companion object {

        private val EMPTY =
            KJsonObjectData<Any?, Any?>(persistentMapOf<String, KJsonData<Any?, Any?>>())

        fun <WT, I> empty(): KJsonObjectData<WT, I> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as KJsonObjectData<WT, I>
        }
    }
}
