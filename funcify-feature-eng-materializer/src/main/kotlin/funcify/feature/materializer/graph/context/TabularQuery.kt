package funcify.feature.materializer.graph.context

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

interface TabularQuery : RequestMaterializationGraphContext {

    val outputColumnNames: ImmutableSet<String>

    val unhandledOutputColumnNames: ImmutableList<String>

    fun update(transformer: Builder.() -> Builder): TabularQuery

    interface Builder : RequestMaterializationGraphContext.Builder<Builder> {

        fun outputColumnNames(outputColumnNames: ImmutableSet<String>): Builder

        fun addUnhandledColumnName(unhandledColumnName: String): Builder

        fun dropHeadUnhandledColumnName(): Builder

        fun build(): TabularQuery
    }
}
